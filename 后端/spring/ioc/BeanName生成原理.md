# BeanName如何自动生成的

![image-20231007110539204](./assets/image-20231007110539204.png)

主要是通过`BeanNameGenerator`来完成 BeanName 的处理：

1. `DefaultBeanNameGenerator`中实现了默认 Name 的生成。
2. `AnnotationBeanNameGenerator`中主要是针对实现了`@Component`等注解的 Bean 实现其 Name，而对应的这些使用注解的类，其没有名称的话也是通过这个类来生成的。

# 默认 Name 生成

对于一般情况下来说，默认的 Name 的生成主要是通过上面提的`DefaultBeanNameGenerator`完成的：

```java
public class DefaultBeanNameGenerator implements BeanNameGenerator {

	/**
	 * A convenient constant for a default {@code DefaultBeanNameGenerator} instance,
	 * as used for {@link AbstractBeanDefinitionReader} setup.
	 * @since 5.2
	 */
	public static final DefaultBeanNameGenerator INSTANCE = new DefaultBeanNameGenerator();


	@Override
	public String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
		return BeanDefinitionReaderUtils.generateBeanName(definition, registry);
	}

}
```

其核心方法是`generateBeanName`，通过代理调用了真正的实现：

```java
public static String generateBeanName(
        BeanDefinition definition, BeanDefinitionRegistry registry, boolean isInnerBean)
        throws BeanDefinitionStoreException {
	// 获取到 xml 中配置的 class 属性值
    String generatedBeanName = definition.getBeanClassName();
    // 如果没有 class 属性值，则使用 parentName+$child 作为 beanName
    if (generatedBeanName == null) {
        if (definition.getParentName() != null) {
            generatedBeanName = definition.getParentName() + "$child";
        }
        // 如果 parentName 也没有的话，就使用 FactoryBeanName 来进行拼接
        else if (definition.getFactoryBeanName() != null) {
            generatedBeanName = definition.getFactoryBeanName() + "$created";
        }
    }
    // 异常 case，上面的一条都没走到
    if (!StringUtils.hasText(generatedBeanName)) {
        throw new BeanDefinitionStoreException("Unnamed bean definition specifies neither " +
                "'class' nor 'parent' nor 'factory-bean' - can't generate bean name");
    }

    if (isInnerBean) {
        // Inner bean: generate identity hashcode suffix.
        return generatedBeanName + GENERATED_BEAN_NAME_SEPARATOR + ObjectUtils.getIdentityHexString(definition);
    }
	
    // 这个是实际上我们的默认 BeanName 的实现
    // Top-level bean: use plain class name with unique suffix if necessary.
    return uniqueBeanName(generatedBeanName, registry);
}

public static String uniqueBeanName(String beanName, BeanDefinitionRegistry registry) {
    String id = beanName;
    int counter = -1;

    // Increase counter until the id is unique.
    // GENERATED_BEAN_NAME_SEPARATOR 就是 #，这里就是拼接出来：beanName+'#'+数字
    // 例如：com.ss.example.User#1
    String prefix = beanName + GENERATED_BEAN_NAME_SEPARATOR;
    while (counter == -1 || registry.containsBeanDefinition(id)) {
        counter++;
        id = prefix + counter;
    }
    return id;
}
```

另外就是需要注意一个小情况：只有一个 bean 的时候，`com.ss.example.User`等价于`com.ss.example.User#0`，这个可以在函数`BeanDefinitionParserDelegate#parseBeanDefinitionElement`找到答案。

# id 和 Name 属性的处理

这两个属性的处理刚好在函数`BeanDefinitionParserDelegate#parseBeanDefinitionElement` 中：

```java
@Nullable
public BeanDefinitionHolder parseBeanDefinitionElement(Element ele, @Nullable BeanDefinition containingBean) {
    // 获取到 id 属性
    String id = ele.getAttribute(ID_ATTRIBUTE);
    // 获取到 name 属性
    String nameAttr = ele.getAttribute(NAME_ATTRIBUTE);

    List<String> aliases = new ArrayList<>();
    if (StringUtils.hasLength(nameAttr)) {
        // MULTI_VALUE_ATTRIBUTE_DELIMITERS 实际上是 ;,' ' 三个符号
        // 因此我们的 name 可以使用分隔符来进行处理
        String[] nameArr = StringUtils.tokenizeToStringArray(nameAttr, MULTI_VALUE_ATTRIBUTE_DELIMITERS);
        aliases.addAll(Arrays.asList(nameArr));
    }
	
    // 使用 id 作为 beanName
    String beanName = id;
    // bean 标签没有 id 属性但是有 name 属性
    if (!StringUtils.hasText(beanName) && !aliases.isEmpty()) {
        // 将 name 拆出来第一项作为 beanName
        beanName = aliases.remove(0);
        if (logger.isTraceEnabled()) {
            logger.trace("No XML 'id' specified - using '" + beanName +
                    "' as bean name and " + aliases + " as aliases");
        }
    }

   // ...

    return null;
}
```

如果上面获取到的 beanName 还是为空了，就是回到了上面一章的逻辑，使用默认 beanName。

