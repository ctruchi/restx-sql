package restx.factory.processor;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.samskivert.mustache.Template;
import restx.common.Mustaches;
import restx.common.processor.RestxAbstractProcessor;
import restx.factory.NamedComponent;
import restx.factory.SqlAlternative;
import restx.factory.SqlComponent;
import restx.factory.When;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.inject.Inject;
import javax.inject.Named;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@SupportedAnnotationTypes("restx.factory.SqlComponent")
@SupportedOptions({"debug"})
public class SqlComponentAnnotationProcessor extends RestxAbstractProcessor {

    final Template componentMachineTpl;
    final Template conditionalMachineTpl;

    public SqlComponentAnnotationProcessor() {
        componentMachineTpl = Mustaches.compile(SqlComponentAnnotationProcessor.class, "SqlComponentMachine.mustache");
        conditionalMachineTpl =
                Mustaches.compile(SqlComponentAnnotationProcessor.class, "SqlConditionalMachine.mustache");
    }

    @Override
    protected boolean processImpl(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) throws Exception {
        processComponents(roundEnv);
        processAlternatives(roundEnv);

        return true;
    }

    private void processComponents(RoundEnvironment roundEnv) {
        for (Element elem : roundEnv.getElementsAnnotatedWith(SqlComponent.class)) {
            try {
                if (!(elem instanceof TypeElement)) {
                    error("annotating element " + elem + " of type " + elem.getKind().name()
                            + " with @Component is not supported", elem);
                    continue;
                }
                TypeElement component = (TypeElement) elem;

                ExecutableElement exec = findInjectableConstructor(component);

                SqlComponent componentAnnotation = component.getAnnotation(SqlComponent.class);

                TypeElement asClass = null;
                try {
                    componentAnnotation.asClass();
                } catch (MirroredTypeException mte) {
                    asClass = asTypeElement(mte.getTypeMirror());
                }
                if (asClass == null) {
                    // no class as been forced, so use the annotated class
                    asClass = component;
                }

                ComponentClass componentClass = new ComponentClass(
                        component.getQualifiedName().toString(),
                        getPackage(component).getQualifiedName().toString(),
                        component.getSimpleName().toString(),
                        asClass.getQualifiedName().toString(),
                        getInjectionName(component.getAnnotation(Named.class)),
                        componentAnnotation.priority(),
                        component);

                buildInjectableParams(exec, componentClass.parameters);

                When when = component.getAnnotation(When.class);
                if (when == null) {
                    generateMachineFile(componentClass);
                } else {
                    generateMachineFile(componentClass, when);
                }
            } catch (Exception e) {
                fatalError("error when processing " + elem, e, elem);
            }
        }
    }

    private void processAlternatives(RoundEnvironment roundEnv) throws IOException {
        for (Element elem : roundEnv.getElementsAnnotatedWith(SqlAlternative.class)) {
            try {
                if (elem instanceof ExecutableElement && elem.getKind() == ElementKind.METHOD) {
                    // skip this annotation, if it is in a module, it will been managed by processModules
                    continue;
                }

                if (!(elem instanceof TypeElement)) {
                    error("annotating element " + elem + " of type " + elem.getKind().name()
                            + " with @Alternative is not supported", elem);
                    continue;
                }
                TypeElement component = (TypeElement) elem;

                ExecutableElement exec = findInjectableConstructor(component);

                SqlAlternative alternative = component.getAnnotation(SqlAlternative.class);
                TypeElement alternativeTo = null;
                if (alternative != null) {
                    try {
                        alternative.to();
                    } catch (MirroredTypeException mte) {
                        alternativeTo = asTypeElement(mte.getTypeMirror());
                    }
                }

                // generate the name for the alternative, could be:
                // - the "named" value if defined
                // - the value of @Named of the referenced component if defined
                // - the referenced component simple name class, if none of the above
                String namedAttribute = alternative.named();
                Optional<String> injectionName;
                if (!namedAttribute.isEmpty()) {
                    injectionName = Optional.of(namedAttribute);
                } else {
                    injectionName = getInjectionName(alternativeTo.getAnnotation(Named.class));
                }

                ComponentClass componentClass = new ComponentClass(
                        component.getQualifiedName().toString(),
                        getPackage(component).getQualifiedName().toString(),
                        component.getSimpleName().toString(),
                        getInjectionName(component.getAnnotation(Named.class)),
                        alternative.priority(),
                        component);

                ComponentClass alternativeToComponentClass = new ComponentClass(
                        alternativeTo.getQualifiedName().toString(),
                        getPackage(alternativeTo).getQualifiedName().toString(),
                        alternativeTo.getSimpleName().toString(),
                        injectionName,
                        alternative.priority(),
                        alternativeTo);

                When when = component.getAnnotation(When.class);
                if (when == null) {
                    error("an Alternative MUST be annotated with @When to tell when it must be activated", elem);
                    continue;
                }

                Named named = component.getAnnotation(Named.class);
                if (named != null) {
                    warn("to specify a 'name' for an Alternative use 'named' attribute, Named annotation will be ignored", elem);
                }

                buildInjectableParams(exec, componentClass.parameters);

                generateMachineFile(componentClass, alternativeToComponentClass, when);
            } catch (Exception e) {
                fatalError("error when processing " + elem, e, elem);
            }
        }
    }


    private Optional<String> getInjectionName(Named named) {
        return named != null ? Optional.of(named.value()) : Optional.<String>absent();
    }

    private void buildInjectableParams(ExecutableElement executableElement, List<InjectableParameter> parameters) {
        for (VariableElement p : executableElement.getParameters()) {
            parameters.add(new InjectableParameter(
                    p.asType(),
                    p.getSimpleName().toString(),
                    getInjectionName(p.getAnnotation(Named.class))
            ));
        }
    }

    private ExecutableElement findInjectableConstructor(TypeElement component) {
        ExecutableElement exec = null;
        for (Element element : component.getEnclosedElements()) {
            if (element instanceof ExecutableElement && element.getKind() == ElementKind.CONSTRUCTOR) {
                if (exec == null
                        || element.getAnnotation(Inject.class) != null) {
                    exec = (ExecutableElement) element;
                    if (exec.getAnnotation(Inject.class) != null) {
                        // if a constructor is marked with @Inject we use it whatever other constructors are available
                        return exec;
                    }
                }
            }
        }
        return exec;
    }

    private void generateMachineFile(ComponentClass componentClass, ComponentClass alternativeTo, When when) throws IOException {
        ImmutableMap<String, Object> ctx = ImmutableMap.<String, Object>builder()
                .put("package", componentClass.pack)
                .put("machine", componentClass.name + "FactoryMachine")
                .put("imports", ImmutableList.of(componentClass.fqcn, alternativeTo.fqcn))
                .put("componentType", componentClass.name)
                .put("componentInjectionType", alternativeTo.name)
                .put("priority", String.valueOf(componentClass.priority))
                .put("whenName", when.name())
                .put("whenValue", when.value())
                .put("componentInjectionName", alternativeTo.injectionName.or(alternativeTo.name))
                .put("conditionalFactoryMachineName", componentClass.name + alternativeTo.name + "Alternative")
                .put("queriesDeclarations", Joiner.on("\n").join(buildQueriesDeclarationsCode(componentClass.parameters)))
                .put("queries", Joiner.on(",\n").join(buildQueriesNames(componentClass.parameters)) +
                        (componentClass.parameters.isEmpty() ? "" : ","))
                .put("parameters", Joiner.on(",\n").join(buildParamFromSatisfiedBomCode(componentClass.parameters)))
                .put("parameterClasses", Joiner.on(",\n").join(buildParamClasses(componentClass.parameters)))
                .build();

        generateJavaClass(componentClass.pack + "." + componentClass.name + "FactoryMachine", conditionalMachineTpl, ctx,
                Collections.singleton(componentClass.originatingElement));
    }

    private void generateMachineFile(ComponentClass componentClass, When when) throws IOException {
        ImmutableMap<String, Object> ctx = ImmutableMap.<String, Object>builder()
                .put("package", componentClass.pack)
                .put("machine", componentClass.name + "FactoryMachine")
                .put("imports", ImmutableList.of(componentClass.fqcn))
                .put("componentType", componentClass.name)
                .put("componentInjectionType", componentClass.producedName)
                .put("priority", String.valueOf(componentClass.priority))
                .put("whenName", when.name())
                .put("whenValue", when.value())
                .put("componentInjectionName", componentClass.injectionName.isPresent() ?
                        componentClass.injectionName.get() : componentClass.name)
                .put("conditionalFactoryMachineName", componentClass.name + componentClass.name + "Conditional")
                .put("queriesDeclarations",
                        Joiner.on("\n").join(buildQueriesDeclarationsCode(componentClass.parameters)))
                .put("queries", Joiner.on(",\n").join(buildQueriesNames(componentClass.parameters)) +
                        (componentClass.parameters.isEmpty() ? "" : ","))
                .put("parameters", Joiner.on(",\n").join(buildParamFromSatisfiedBomCode(componentClass.parameters)))
                .put("parameterClasses", Joiner.on(",\n").join(buildParamClasses(componentClass.parameters)))
                .build();

        generateJavaClass(componentClass.pack + "." + componentClass.name + "FactoryMachine",
                conditionalMachineTpl,
                ctx,
                Collections.singleton(componentClass.originatingElement));
    }

    private void generateMachineFile(ComponentClass componentClass) throws IOException {
        ImmutableMap<String, String> ctx = ImmutableMap.<String, String>builder()
                .put("package", componentClass.pack)
                .put("machine", componentClass.name + "FactoryMachine")
                .put("componentFqcn", componentClass.fqcn)
                .put("componentType", componentClass.name)
                .put("componentProducedType", componentClass.producedName)
                .put("priority", String.valueOf(componentClass.priority))
                .put("componentInjectionName", componentClass.injectionName.isPresent() ?
                        componentClass.injectionName.get() : componentClass.name)
                .put("queriesDeclarations",
                        Joiner.on("\n").join(buildQueriesDeclarationsCode(componentClass.parameters)))
                .put("queries", Joiner.on(",\n").join(buildQueriesNames(componentClass.parameters)) +
                        (componentClass.parameters.isEmpty() ? "" : ","))
                .put("parameters", Joiner.on(",\n").join(buildParamFromSatisfiedBomCode(componentClass.parameters)))
                .put("parameterClasses", Joiner.on(",\n").join(buildParamClasses(componentClass.parameters)))
                .build();

        generateJavaClass(componentClass.pack + "." + componentClass.name + "FactoryMachine", componentMachineTpl, ctx,
                Collections.singleton(componentClass.originatingElement));

    }

    private List<String> buildQueriesDeclarationsCode(List<InjectableParameter> parameters) {
        List<String> parametersCode = Lists.newArrayList();
        for (InjectableParameter parameter : parameters) {
            parametersCode.add(parameter.getQueryDeclarationCode());
        }
        return parametersCode;
    }

    private List<String> buildQueriesNames(List<InjectableParameter> parameters) {
        List<String> parametersCode = Lists.newArrayList();
        for (InjectableParameter parameter : parameters) {
            parametersCode.add(parameter.name);
        }
        return parametersCode;
    }

    private List<String> buildParamFromSatisfiedBomCode(List<InjectableParameter> parameters) {
        List<String> parametersCode = Lists.newArrayList();
        for (InjectableParameter parameter : parameters) {
            parametersCode.add(parameter.getFromSatisfiedBomCode());
        }
        return parametersCode;
    }

    private List<String> buildParamClasses(List<InjectableParameter> parameters) {
        List<String> parameterClasses = Lists.newArrayList();
        for (InjectableParameter parameter : parameters) {
            if (parameter.baseType instanceof DeclaredType) {
                parameterClasses.add(((DeclaredType) parameter.baseType).asElement() + ".class");
            } else {
                parameterClasses.add(parameter.baseType.toString() + ".class");
            }
        }
        return parameterClasses;
    }

    private static class ComponentClass {

        final String fqcn;

        final List<InjectableParameter> parameters = Lists.newArrayList();
        final Element originatingElement;
        final String pack;
        final String name;
        final String producedName;
        final int priority;
        final Optional<String> injectionName;

        ComponentClass(String fqcn,
                       String pack, String name,
                       Optional<String> injectionName, int priority, Element originatingElement) {
            this(fqcn, pack, name, name, injectionName, priority, originatingElement);
        }

        ComponentClass(String fqcn,
                       String pack, String name, String producedName,
                       Optional<String> injectionName, int priority, Element originatingElement) {
            this.fqcn = fqcn;
            this.injectionName = injectionName;
            this.priority = priority;

            this.pack = pack;
            this.name = name;
            this.producedName = producedName;
            this.originatingElement = originatingElement;
        }
    }

    private static class InjectableParameter {

        private static final Class[] iterableClasses = new Class[]{
                Iterable.class, Collection.class, List.class, Set.class,
                ImmutableList.class, ImmutableSet.class};

        final TypeMirror baseType;
        final String name;
        final Optional<String> injectionName;

        private InjectableParameter(TypeMirror baseType, String name, Optional<String> injectionName) {
            this.baseType = baseType;
            this.name = name;
            this.injectionName = injectionName;
        }

        public String getQueryDeclarationCode() {
            TypeMirror targetType = targetType(baseType);
            String optionalOrNotQueryQualifier =
                    isGuavaOptionalType(baseType) || isJava8OptionalType(baseType) || isMultiType(baseType) ?
                            "optional()" : "mandatory()";

            if (injectionName.isPresent()) {
                return String
                        .format("private final Factory.Query<%s> %s = Factory.Query.byName(Name.of(%s, \"%s\")).%s;",
                                targetType,
                                name,
                                targetType + ".class",
                                injectionName.get(),
                                optionalOrNotQueryQualifier);
            } else {
                return String.format("private final Factory.Query<%s> %s = Factory.Query.byClass(%s).%s;",
                        targetType, name, targetType + ".class", optionalOrNotQueryQualifier);
            }
        }

        public String getFromSatisfiedBomCode() {
            if (isGuavaOptionalType(baseType)) {
                return String.format("satisfiedBOM.getOneAsComponent(%s)", name);
            } else if (isJava8OptionalType(baseType)) {
                return String
                        .format("java.util.Optional.ofNullable(satisfiedBOM.getOneAsComponent(%s).orNull())", name);
            } else if (isNamedComponentType(baseType)) {
                return String.format("satisfiedBOM.getOne(%s).get()", name);
            } else if (isMultiType(baseType)) {
                TypeMirror pType = parameterType(baseType).get();
                String code;
                if (isNamedComponentType(pType)) {
                    code = String.format("satisfiedBOM.get(%s)", name);
                } else {
                    code = String.format("satisfiedBOM.getAsComponents(%s)", name);
                }
                if (baseType.toString().startsWith(Collection.class.getCanonicalName())
                        || baseType.toString().startsWith(List.class.getCanonicalName())) {
                    code = String.format("com.google.common.collect.Lists.newArrayList(%s)", code);
                } else if (baseType.toString().startsWith(Set.class.getCanonicalName())) {
                    code = String.format("com.google.common.collect.Sets.newLinkedHashSet(%s)", code);
                } else if (baseType.toString().startsWith(ImmutableList.class.getCanonicalName())) {
                    code = String.format("com.google.common.collect.ImmutableList.copyOf(%s)", code);
                } else if (baseType.toString().startsWith(ImmutableSet.class.getCanonicalName())) {
                    code = String.format("com.google.common.collect.ImmutableSet.copyOf(%s)", code);
                }
                return code;
            } else {
                return String.format("satisfiedBOM.getOne(%s).get().getComponent()", name);
            }
        }

        private TypeMirror targetType(TypeMirror type) {
            if (isGuavaOptionalType(type) || isJava8OptionalType(type)
                    || isMultiType(type) || isNamedComponentType(type)) {
                Optional<TypeMirror> pType = parameterType(type);
                if (!pType.isPresent()) {
                    throw new RuntimeException(
                            "Optional | Collection | NamedComponent type for parameter " + name + " needs" +
                                    " parameterized type (generics) to be processed correctly");
                }
                return targetType(pType.get());
            } else {
                return type;
            }
        }

        private Optional<TypeMirror> parameterType(TypeMirror type) {
            if (type instanceof DeclaredType) {
                DeclaredType declaredBaseType = (DeclaredType) type;
                if (declaredBaseType.getTypeArguments().isEmpty()) {
                    return Optional.absent();
                }
                return Optional.of(declaredBaseType.getTypeArguments().get(0));
            } else {
                return Optional.absent();
            }
        }

        private boolean isGuavaOptionalType(TypeMirror type) {
            return type.toString().startsWith(Optional.class.getCanonicalName());
        }

        private boolean isJava8OptionalType(TypeMirror type) {
            return type.toString().startsWith("java.util.Optional");
        }

        private boolean isNamedComponentType(TypeMirror type) {
            return type.toString().startsWith(NamedComponent.class.getCanonicalName());
        }

        private boolean isMultiType(TypeMirror type) {
            for (Class it : iterableClasses) {
                if (type.toString().startsWith(it.getCanonicalName())) {
                    return true;
                }
            }
            return false;
        }
    }
}
