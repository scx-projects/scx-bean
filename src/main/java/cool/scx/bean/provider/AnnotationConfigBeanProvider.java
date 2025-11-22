package cool.scx.bean.provider;

import cool.scx.bean.BeanFactory;
import cool.scx.bean.BeanResolutionContext;
import cool.scx.bean.BeanResolutionContext.DependencyContext;
import cool.scx.bean.annotation.PreferredConstructor;
import cool.scx.bean.exception.BeanCreationException;
import cool.scx.bean.exception.IllegalBeanClassException;
import cool.scx.bean.exception.NoSuchConstructorException;
import cool.scx.bean.exception.NoUniqueConstructorException;
import dev.scx.reflect.ClassInfo;
import dev.scx.reflect.ConstructorInfo;
import dev.scx.reflect.ParameterInfo;
import dev.scx.reflect.ScxReflect;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static dev.scx.reflect.AccessModifier.PUBLIC;
import static dev.scx.reflect.ClassKind.*;

/// 注解配置的 BeanProvider, 根据 class 进行反射
///
/// @author scx567888
/// @version 0.0.1
public final class AnnotationConfigBeanProvider implements BeanProvider {

    private final Class<?> beanClass;
    private final ConstructorInfo constructor;

    public AnnotationConfigBeanProvider(Class<?> beanClass) throws IllegalBeanClassException, NoSuchConstructorException, NoUniqueConstructorException {
        checkClass(beanClass);
        this.beanClass = beanClass;
        this.constructor = findPreferredConstructor(beanClass);
    }

    public static void checkClass(Class<?> beanClass) throws IllegalBeanClassException {
        var typeInfo = ScxReflect.typeOf(beanClass);
        ClassInfo classInfo;
        if (typeInfo instanceof ClassInfo c) {
            classInfo = c;
        } else {
            throw new IllegalBeanClassException("beanClass " + beanClass.getName() + " 不支持非普通类 ");
        }
        var classType = classInfo.classKind();
        if (classType == CLASS) {
            if (classInfo.isMemberClass() && !classInfo.isStatic()) {
                throw new IllegalBeanClassException("beanClass " + beanClass.getName() + " 不支持非静态的成员类 ");
            }
            if (classInfo.isAbstract()) {
                throw new IllegalBeanClassException("beanClass " + beanClass.getName() + " is an abstract class");
            }
        }
        if (classType == INTERFACE) {
            throw new IllegalBeanClassException("beanClass " + beanClass.getName() + " is an interface");
        }
        if (classType == ANNOTATION) {
            throw new IllegalBeanClassException("beanClass " + beanClass.getName() + " is an annotation");
        }
        if (classType == ENUM) {
            throw new IllegalBeanClassException("beanClass " + beanClass.getName() + " is an enum");
        }
    }

    /// 解析构造函数参数
    public static Object resolveConstructorArgument(BeanFactory beanFactory, ParameterInfo parameter, BeanResolutionContext beanResolutionContext) throws Exception {
        for (var dependencyResolver : beanFactory.beanDependencyResolvers()) {
            var value = dependencyResolver.resolveConstructorArgument(parameter, beanResolutionContext);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /// 查找构造函数
    public static ConstructorInfo findPreferredConstructor(Class<?> beanClass) throws NoSuchConstructorException, NoUniqueConstructorException {
        // 这里之前已经检查过 beanClass 了 这里直接强转
        var classInfo = (ClassInfo) ScxReflect.typeOf(beanClass);
        // 我们只使用 public 的 构造函数
        var publicConstructors = new ArrayList<ConstructorInfo>();
        for (var constructor : classInfo.constructors()) {
            if (constructor.accessModifier() == PUBLIC) {
                publicConstructors.add(constructor);
            }
        }

        //一个都没有 报错
        if (publicConstructors.isEmpty()) {
            throw new NoSuchConstructorException("无法找到类 " + beanClass.getName() + " 的任何 public 构造方法," + "至少需要一个 public 构造方法用于创建 Bean.");
        }

        //只找到一个直接用
        if (publicConstructors.size() == 1) {
            return publicConstructors.get(0);
        }

        //找到多个 需要查看是否有切只有一个标注了 PreferredConstructor 注解的
        var preferredConstructors = new ArrayList<ConstructorInfo>();
        for (var constructorInfo : publicConstructors) {
            if (constructorInfo.findAnnotation(PreferredConstructor.class) != null) {
                preferredConstructors.add(constructorInfo);
            }
        }
        if (preferredConstructors.isEmpty()) {
            throw new NoUniqueConstructorException(
                "在类 " + beanClass.getName() + " 中检测到多个 public 构造方法, 且都未标注 @PreferredConstructor 注解," +
                    "无法确定应使用哪个构造方法. \n" +
                    "可用的 public 构造方法列表: \n" + formatConstructors(publicConstructors) +
                    "\n请在期望使用的构造方法上添加 @PreferredConstructor 注解. "
            );
        }
        if (preferredConstructors.size() == 1) {
            return preferredConstructors.get(0);
        }

        throw new NoUniqueConstructorException(
            "在类 " + beanClass.getName() + " 中检测到多个标注了 @PreferredConstructor 注解的 public 构造方法, " +
                "无法唯一确定使用哪个构造方法. \n" +
                "冲突的构造方法列表: \n" + formatConstructors(preferredConstructors) +
                "\n同一个类中只能有一个构造方法标注 @PreferredConstructor, 请检查修正."
        );

    }

    /// 美化构造函数输出
    private static String formatConstructors(List<ConstructorInfo> constructors) {
        var builder = new StringBuilder();
        for (var constructor : constructors) {
            builder.append("  - ")
                .append(constructor.rawConstructor().toGenericString())
                .append("\n");
        }
        return builder.toString();
    }

    @Override
    public Object getBean(BeanFactory beanFactory, BeanResolutionContext beanResolutionContext) throws BeanCreationException {

        var parameters = constructor.parameters();
        var objects = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i = i + 1) {
            var parameter = parameters[i];
            // 开始循环依赖检查
            // 虽然这里我们无法得知 经过多层包装的最终 BeanProvider 的 singleton 的值, 但是这对依赖检查没有影响, 此处硬编码 false
            beanResolutionContext.startDependencyCheck(new DependencyContext(this.beanClass, false, this.constructor, parameter));
            try {
                objects[i] = resolveConstructorArgument(beanFactory, parameter, beanResolutionContext);
            } catch (Exception e) {
                throw new BeanCreationException("在类 " + this.beanClass.getName() + "中, 解析构造参数 " + parameter.name() + " 时发生异常 ", e);
            } finally {
                //结束检查
                beanResolutionContext.endDependencyCheck();
            }
        }

        try {
            return constructor.newInstance(objects);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new BeanCreationException("在类 " + this.beanClass.getName() + "中, 创建 bean 时发生异常 ", e);
        } catch (InvocationTargetException e) {
            throw new BeanCreationException("在类 " + this.beanClass.getName() + "中, 创建 bean 时发生异常 ", e.getCause());
        }
    }

    @Override
    public Class<?> beanClass() {
        return beanClass;
    }

    @Override
    public boolean singleton() {
        return false;
    }

}
