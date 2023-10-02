/**
 * 这个类主要是用来描述一个Bean的定义，将 json 中的类信息解析出来，然后封装到这个类中
 * (Spring 中是将 XML 中的信息解析出来，然后封装到 BeanDefinition 中)
 */
class BeanDefinition {
    var clazz: String? = null
    var id: String? = null
}