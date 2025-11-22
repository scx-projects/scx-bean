package cool.scx.bean.dependency_resolver;

import cool.scx.bean.BeanFactory;
import cool.scx.bean.BeanResolutionContext;
import cool.scx.bean.annotation.Autowired;
import cool.scx.bean.exception.BeanCreationException;
import cool.scx.bean.exception.NoSuchBeanException;
import cool.scx.bean.exception.NoUniqueBeanException;
import cool.scx.common.constant.AnnotationValues;
import dev.scx.reflect.FieldInfo;
import dev.scx.reflect.ParameterInfo;

/// 处理 Autowired 注解 同时也承担最核心的 配置
///
/// @author scx567888
/// @version 0.0.1
public final class AutowiredAnnotationDependencyResolver implements BeanDependencyResolver {

    private final BeanFactory beanFactory;

    public AutowiredAnnotationDependencyResolver(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public Object resolveConstructorArgument(ParameterInfo parameter, BeanResolutionContext beanResolutionContext) throws BeanCreationException, NoSuchBeanException, NoUniqueBeanException {
        // 构造参数和 fieldValue 规则略有不同, 强制注入
        var autowired = parameter.findAnnotation(Autowired.class);
        var name = autowired == null ? null : AnnotationValues.getRealValue(autowired.value());
        if (name != null) {
            return beanFactory.getBean(name, parameter.rawParameter().getType(), beanResolutionContext);
        } else {
            return beanFactory.getBean(parameter.rawParameter().getType(), beanResolutionContext);
        }
    }

    @Override
    public Object resolveFieldValue(FieldInfo field, BeanResolutionContext beanResolutionContext) throws BeanCreationException, NoSuchBeanException, NoUniqueBeanException {
        // 字段 只处理有 Autowired 注解的
        var autowired = field.findAnnotation(Autowired.class);
        if (autowired == null) {
            return null;
        }
        var name = AnnotationValues.getRealValue(autowired.value());
        if (name != null) {
            return beanFactory.getBean(name, field.rawField().getType(), beanResolutionContext);
        } else {
            return beanFactory.getBean(field.rawField().getType(), beanResolutionContext);
        }
    }

}
