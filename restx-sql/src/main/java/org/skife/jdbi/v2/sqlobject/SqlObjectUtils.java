package org.skife.jdbi.v2.sqlobject;

import org.skife.jdbi.cglib.proxy.Callback;
import org.skife.jdbi.cglib.proxy.CallbackFilter;
import org.skife.jdbi.cglib.proxy.Enhancer;
import org.skife.jdbi.cglib.proxy.Factory;
import org.skife.jdbi.cglib.proxy.MethodInterceptor;
import org.skife.jdbi.cglib.proxy.MethodProxy;
import org.skife.jdbi.cglib.proxy.NoOp;
import org.skife.jdbi.com.fasterxml.classmate.MemberResolver;
import org.skife.jdbi.com.fasterxml.classmate.ResolvedType;
import org.skife.jdbi.com.fasterxml.classmate.ResolvedTypeWithMembers;
import org.skife.jdbi.com.fasterxml.classmate.TypeResolver;
import org.skife.jdbi.com.fasterxml.classmate.members.ResolvedMethod;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


public class SqlObjectUtils {

    private static final TypeResolver typeResolver  = new TypeResolver();
    private static final Map<Method, Handler> mixinHandlers = new HashMap<Method, Handler>();
    private static final ConcurrentMap<Class<?>, Map<Method, Handler>>
            handlersCache = new ConcurrentHashMap<Class<?>, Map<Method, Handler>>();
    private static final ConcurrentMap<Class<?>, Factory>              factories     = new ConcurrentHashMap<Class<?>, Factory>();

    private static Method jdk8DefaultMethod = null;

    static {
        mixinHandlers.putAll(TransactionalHelper.handlers());
        mixinHandlers.putAll(GetHandleHelper.handlers());
        mixinHandlers.putAll(TransmogrifierHelper.handlers());

        try {
            SqlObjectUtils.jdk8DefaultMethod = Method.class.getMethod("isDefault");
        }
        catch (NoSuchMethodException e) {
            // fallthrough, expected on e.g. JDK7
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T buildSqlObject(final Class<T> sqlObjectType, final HandleDing handle,
                                Class[] constructorArgumentTypes, Object[] constructorArguments)
    {
        Factory f;
        if (factories.containsKey(sqlObjectType)) {
            f = factories.get(sqlObjectType);
        }
        else {
            Enhancer e = new Enhancer();
            e.setClassLoader(sqlObjectType.getClassLoader());

            List<Class> interfaces = new ArrayList<Class>();
            interfaces.add(CloseInternalDoNotUseThisClass.class);
            if (sqlObjectType.isInterface()) {
                interfaces.add(sqlObjectType);
            }
            else {
                e.setSuperclass(sqlObjectType);
            }
            e.setInterfaces(interfaces.toArray(new Class[interfaces.size()]));
            final SqlObject so = new SqlObject(buildHandlersFor(sqlObjectType), handle);

            e.setCallbackFilter(new CallbackFilter() {

                @Override
                public int accept(Method method) {
                    if (jdk8DefaultMethod == null) {
                        return 0;
                    }
                    else {
                        try {
                            Boolean result = (Boolean) jdk8DefaultMethod.invoke(method);
                            return Boolean.TRUE.equals(result) ? 1 : 0;
                        } catch (IllegalArgumentException e) {
                            return 0;
                        } catch (IllegalAccessException e) {
                            return 0;
                        } catch (InvocationTargetException e) {
                            return 0;
                        }
                    }
                }

            });

            e.setCallbacks(new Callback[] {
                    new MethodInterceptor() {
                        @Override
                        public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
                            return so.invoke(o, method, objects, methodProxy);
                        }
                    },
                    NoOp.INSTANCE
            });
            T t;
            if (constructorArgumentTypes.length > 0) {
                t = (T) e.create(constructorArgumentTypes, constructorArguments);
            } else {
                t = (T) e.create();
            }
            T actual = (T) factories.putIfAbsent(sqlObjectType, (Factory) t);
            if (actual == null) {
                return t;
            }
            f = (Factory) actual;
        }

        final SqlObject so = new SqlObject(buildHandlersFor(sqlObjectType), handle);
        Callback[] callbacks = {
                new MethodInterceptor() {
                    @Override
                    public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy)
                            throws Throwable {
                        return so.invoke(o, method, objects, methodProxy);
                    }
                },
                NoOp.INSTANCE
        };

        Object instance;
        if (constructorArgumentTypes.length > 0) {
            instance = f.newInstance(constructorArgumentTypes, constructorArguments, callbacks);
        } else {
            instance = f.newInstance(callbacks);
        }
        return (T) instance;
    }

    private static Map<Method, Handler> buildHandlersFor(Class<?> sqlObjectType)
    {
        if (handlersCache.containsKey(sqlObjectType)) {
            return handlersCache.get(sqlObjectType);
        }

        final MemberResolver mr = new MemberResolver(typeResolver);
        final ResolvedType sql_object_type = typeResolver.resolve(sqlObjectType);

        final ResolvedTypeWithMembers d = mr.resolve(sql_object_type, null, null);

        final Map<Method, Handler> handlers = new HashMap<Method, Handler>();
        for (final ResolvedMethod method : d.getMemberMethods()) {
            final Method raw_method = method.getRawMember();

            if (raw_method.isAnnotationPresent(SqlQuery.class)) {
                handlers.put(raw_method, new QueryHandler(sqlObjectType, method, ResultReturnThing.forType(method)));
            }
            else if (raw_method.isAnnotationPresent(SqlUpdate.class)) {
                handlers.put(raw_method, new UpdateHandler(sqlObjectType, method));
            }
            else if (raw_method.isAnnotationPresent(SqlBatch.class)) {
                handlers.put(raw_method, new BatchHandler(sqlObjectType, method));
            }
            else if (raw_method.isAnnotationPresent(SqlCall.class)) {
                handlers.put(raw_method, new CallHandler(sqlObjectType, method));
            }
            else if(raw_method.isAnnotationPresent(CreateSqlObject.class)) {
                handlers.put(raw_method, new CreateSqlObjectHandler(raw_method.getReturnType()));
            }
            else if (method.getName().equals("close") && method.getRawMember().getParameterTypes().length == 0) {
                handlers.put(raw_method, new CloseHandler());
            }
            else if (raw_method.isAnnotationPresent(Transaction.class)) {
                handlers.put(raw_method, new PassThroughTransactionHandler(raw_method, raw_method.getAnnotation(Transaction.class)));
            }
            else if (mixinHandlers.containsKey(raw_method)) {
                handlers.put(raw_method, mixinHandlers.get(raw_method));
            }
            else {
                handlers.put(raw_method, new PassThroughHandler(raw_method));
            }
        }

        // this is an implicit mixin, not an explicit one, so we need to *always* add it
        handlers.putAll(CloseInternalDoNotUseThisClass.Helper.handlers());

        handlers.putAll(EqualsHandler.handler());
        handlers.putAll(ToStringHandler.handler(sqlObjectType.getName()));
        handlers.putAll(HashCodeHandler.handler());

        handlersCache.put(sqlObjectType, handlers);

        return handlers;
    }
}
