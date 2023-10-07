# Bean 作用域相关

默认情况下，注册到 Spring 容器中的 Bean 是一个单例，在之前的使用的例子中可以看见，拿到的都是同一个对象。从这个角度上看，实际上 Spring 是一个类似于其他语言中的静态部分。

# 修改作用域

上面提到了，默认情况是是以单例存在的，但 Spring 也提供了修改的方案(`Scope`属性)：

```xml
<bean class="com.ss.example.Cat" name="cat" scope="prototype">
    <property name="name" value="Tom"/>
    <property name="age" value="3"/>
</bean>
```

| 取值          | 含义                                                         | 生效条件       |
| ------------- | ------------------------------------------------------------ | -------------- |
| Singleton     | Bean 作为单例存在，在 Spring容器仅存在一个实例               |                |
| prototype     | 每次获取 Bean 的时候才会创建一个实例，是一个单独的对象，类似于 new |                |
| request       | 新的请求到达的时候，会创建一个 Bean                          | Web 环境下生效 |
| session       | 有一个会话创建时会创建一个 Bean                              | Web 环境下生效 |
| application   | 在整个项目生命周期中，只有一个 Bean                          | Web 环境下生效 |
| globalsession | 类似于 application，但是是在 portlet 下才会使用(现在基本不用了) | Web 环境下生效 |

如果是使用 Java 代码的话，如下即可：

```kotlin
/**
 * @Bean 注解相当于在 xml 中配置了一个 bean，方法名相当于 bean 的 id，返回值相当于 bean的 class
 * @Scope 定义了上面的作用域
 */
@Bean
@Scope("prototype")
fun user(cat: Cat): User {
    val user = User()
    user.name = "user1"
    user.age = 18
    user.cat2 = cat
    return user
}
```

我们需要注意的是：

1. Singleton 模式下，在 Spring 容器初始化时就会直接创建这个对象了。但是 prototype 只有在获取 Bean 的时候才会去初始化。
2. 需要对 prototype 类型的 Bean 做生命周期管理(即销毁)，防止内存泄漏。