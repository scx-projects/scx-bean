package cool.scx.bean.dependency_resolver;

import cool.scx.bean.BeanResolutionContext;
import dev.scx.reflect.FieldInfo;
import dev.scx.reflect.ParameterInfo;

/// 提供配置一个 bean 所需的依赖
///
/// @author scx567888
/// @version 0.0.1
public interface BeanDependencyResolver {

    /// 提供 构造函数 参数.
    Object resolveConstructorArgument(ParameterInfo parameter, BeanResolutionContext beanResolutionContext) throws Exception;

    /// 提供 字段 无法处理应返回 null.
    Object resolveFieldValue(FieldInfo field, BeanResolutionContext beanResolutionContext) throws Exception;

}
