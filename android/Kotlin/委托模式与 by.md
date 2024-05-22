# 委托模式与 By 关键字

## 委托模式

委托模式是软件设计模式中的一项基本技巧。在委托模式中，有两个对象参与处理同一个请求，**接受请求的对象**将请求**委托给另一个对象**来处理。

假如我们想实现一个比较复杂的属性，它们处理起来比把值保存在支持字段中更复杂，但是却不想在每个访问器都重复这样的逻辑，于是把获取这个属性实例的工作交给了一个辅助对象，这个辅助对象就是委托。比如可以把这个属性的值保存在数据库中，一个Map中等，而不是直接调用其访问器。

> 访问器：主要就是 get/set 方法，该方法在 kotlin 中是自带的。
>
> 支持字段：支持字段主要就是使用 get/set 方法时，获取到数据的那个 filed。
>
> ```kotlin
> class People{
>     val name: String? = null
>     var age: Int = 0
>         //返回field的值
>         get() = field
>         //设置field的值
>         set(value){
>             Log.i("People", "旧值是$field 新值是$value ")
>             field = value
>         }
> 
>     var isAbove18: Boolean = false
>         get() = age > 18
> }
> 
> ```

一个典型的委托模式的设计可以抽象为下面的代码，我们假设存在一个类 C 需要委托人办一些事，但是有着多个委托人，此时 C 就将其想要做的事抽象为一个接口 I，由 DelegateA 和 DelegateB 两个人同时继承。但是具体选哪个人由 C 决定：

```java
// 定义了委托者需要的功能
interface I {
   void f();
   void g();
}


class DelegateA implements I {
   public void f() { System.out.println("DelegateA: doing f()"); }
   public void g() { System.out.println("DelegateA: doing g()"); }
}

class DelegateB implements I {
   public void f() { System.out.println("DelegateB: doing f()"); }
   public void g() { System.out.println("DelegateB: doing g()"); }
}

class C implements I {
   // delegation，将事情代理给 DelegateA
   I i = new DelegateA();

   public void f() { i.f(); }
   public void g() { i.g(); }

   // normal attributes，切换代理人
   public void toA() { i = new DelegateA(); }
   public void toB() { i = new DelegateB(); }
}


public class Main {
   public static void main(String[] args) {
       C c = new C();
       c.f();     // output: DelegateA: doing f()
       c.g();     // output: DelegateA: doing g()
       c.toB();
       c.f();     // output: DelegateB: doing f()
       c.g();     // output: DelegateB: doing g()
   }
}
```

## by 关键字

### by 一个类

这个是比较少见的，我们知道就行：

```kotlin
interface Base {
    fun printMessage()
    fun printMessageLine()
}

class BaseImpl(val x: Int) : Base {
    override fun printMessage() { print(x) }
    override fun printMessageLine() { println(x) }
}

class Derived(b: Base) : Base by b {
    override fun printMessage() { print("abc") }
}

fun main() {
    val b = BaseImpl(10)
    Derived(b).printMessage()
    Derived(b).printMessageLine()
}
```

更多是下面 by 一个字段的。

### by 一个字段

理解了委托模式之后再来聊 by 这个关键字。我们都知道使用 by 的时候 ide 会提示引入 setter 和 getter 方法，结合 by 本质上就是代理的意思，那基本上 by 的逻辑就呼之欲出了。

```kotlin
class People {
    var delegate: Phone by Phone()
    val id = "aaa"
}

class Phone(private val phoneName: String = "default") {
    companion object {
        private const val TAG = "Phone"
    }

    private val someStr = "PhoneStr=$phoneName"

    fun doSomeString() {
        println("$TAG, do some string=$someStr")
    }

    operator fun getValue(people: People, property: KProperty<*>): Phone {
        println("【获取】，这里返回一个 Phone，操作的 KProperty=${property.name}, people=${people.id}")
        return this
    }

    operator fun setValue(people: People, property: KProperty<*>, phone: Phone) {
        println("【设置】，这里设置一个 Phone，操作的 KProperty=${property.name}, people=${people.id}, phoneStr=${phone.phoneName}")
    }
}
```

然后我们就能够通过 People 类去委托了 Phone 做一些事：

```kotlin
fun main() {
    val people = People()
    people.delegate.doSomeString()
    people.delegate = Phone("Another")
    people.delegate.doSomeString()
}

// 输出
【获取】，这里返回一个 Phone，操作的 KProperty=delegate, people=aaa
Phone, do some string=PhoneStr=default

【设置】，这里设置一个 Phone，操作的 KProperty=delegate, people=aaa, phoneStr=Another
【获取】，这里返回一个 Phone，操作的 KProperty=delegate, people=aaa
Phone, do some string=PhoneStr=default
```

这里比较有意思的是，我们拿出来的 KProperty 是**委托人的 delegate 字段**。

另一个有意思的地方在于，我们通过修改`people.delegate`字段传入一个新的 Phone 对象时，`doSomeString`还是在原本的老 Phone 对象上，这直接展示了，实际上 delegate 的 get/set 方法没有什么特别的，就是**调了两个自定义的操作方法**。

而实际上，我们想在 setter 中添加:

```kotlin
operator fun setValue(people: People, property: KProperty<*>, phone: Phone) {
    println("【设置】，这里设置一个 Phone，操作的 KProperty=${property.name}, people=${people.id}, phoneStr=${phone.phoneName}")

    // 编译出错
    this = phone
}
```

这也符合预期，实际上就是 this 不可变。



这里回答了关键的问题，也即为什么 by 的实现不是完全仿照 Java 的，按照接口的形式，多个实现类完成？

实际上是，by 后面的类，它本身就是代理中转，我们可以理解为中间人，具体的“雇佣兵”由他来选择(来自赛博朋克2077)，或者他自己上，这一点在 Kotlin 书中有介绍：

```kotlin
class Resource

class Owner {
    var varResource: Resource by ResourceDelegate()
}

class ResourceDelegate(private var resource: Resource = Resource()) {
    operator fun getValue(thisRef: Owner, property: KProperty<*>): Resource {
        return resource
    }
    operator fun setValue(thisRef: Owner, property: KProperty<*>, value: Any?) {
        if (value is Resource) {
            resource = value
        }
    }
}
```

上面的代码中，resource 就是中间人的资源，中间人来负责给买家提供服务，此时买家和雇佣兵之间完全解耦。

进一步的的，上面的中间人也可以抽为一个内部类：

```kotlin
fun resourceDelegate(resource: Resource = Resource()): ReadWriteProperty<Any?, Resource> =
    object : ReadWriteProperty<Any?, Resource> {
        var curValue = resource
        override fun getValue(thisRef: Any?, property: KProperty<*>): Resource = curValue
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Resource) {
            curValue = value
        }
    }

val readOnlyResource: Resource by resourceDelegate()  // ReadWriteProperty as val
var readWriteResource: Resource by resourceDelegate()
```

### 基于 Map 的代理

我们可以看到代理很多时候都是需要多个“雇佣兵的”，但是实际上它没有什么黑魔法，就是通过 set/get 去做代理。但是 Kotlin 还提供了另一个方式，让我们直接通过 Map 解决问题：

```kotlin
class User(val map: Map<String, Any?>) {
    val text: String by map
    val age: Int     by map
}

fun main() {
    val user = User(mapOf(
        "name" to "John Doe",
        "age"  to 25,
        "text" to "Ni hao"
    ))

    println(user.text) // Prints "Nihao"
    println(user.age)  // Prints 25
}
```

上面的代码中，user 将两个字段代理给了 Map 中的**对应名字**的 key 所指向的数据，如果我们把类 User 中的第一个字段进行修改：

```kotlin
class User(val map: Map<String, Any?>) {
  	// 修改为 name，此时就会找 map 中的 "name"
    val name: String by map
    val age: Int     by map
}

fun main() {
    val user = User(mapOf(
        "name" to "John Doe",
        "age"  to 25,
        "text" to "Ni hao"
    ))

    println(user.name) // Prints "John Doe"
    println(user.age)  // Prints 25
}
```

### by lazy 和 observable

#### by lazy

我们经常会看见懒加载：

```kotlin
//这里使用by lazy惰性初始化一个实例
val instance: DataStoreManager by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
    DataStoreManager(store) }
```

通过上面的知识，我们知道，`lazy()`函数一定是返回了一个对象，且该对象实现了 get/set 方法，但是细节上面有所差异。因此我们去看下源码：

```kotlin
public actual fun <T> lazy(initializer: () -> T): Lazy<T> = SynchronizedLazyImpl(initializer)
```

确实返回了一个继承了`Lazy`接口的对象`SynchronizedLazyImpl`，初始化方法被放了进去：

```kotlin
// 惰性初始化接口
public interface Lazy<out T> {
    
    // 懒加载的值，一旦被赋值，将不会被改变
    public val value: T

    // 表示是否已经初始化
    public fun isInitialized(): Boolean
}

private class SynchronizedLazyImpl<out T>(initializer: () -> T, lock: Any? = null) : Lazy<T>, Serializable {
  
  	private var initializer: (() -> T)? = initializer
  	
    @Volatile private var _value: Any? = UNINITIALIZED_VALUE
  	
    // final field is required to enable safe publication of constructed instance
  	// 锁实现
    private val lock = lock ?: this
		
  	// 覆写了 value 值
    override val value: T
        get() {
          	// 第一次判空，当实例存在则直接返回
            val _v1 = _value
            if (_v1 !== UNINITIALIZED_VALUE) {
                @Suppress("UNCHECKED_CAST")
                return _v1 as T
            }
						
          	// 使用锁进行同步, 我们一般调 lazy 都是锁的 SynchronizedLazyImpl 对象本身
            return synchronized(lock) {
                val _v2 = _value
              	// 第二次判空
                if (_v2 !== UNINITIALIZED_VALUE) {
                    @Suppress("UNCHECKED_CAST") (_v2 as T)
                } else {
                  	// 真正初始化
                    val typedValue = initializer!!()
                    _value = typedValue
                    initializer = null
                    typedValue
                }
            }
        }

    override fun isInitialized(): Boolean = _value !== UNINITIALIZED_VALUE

    override fun toString(): String = if (isInitialized()) value.toString() else "Lazy value not initialized yet."

    private fun writeReplace(): Any = InitializedLazyImpl(value)
}
```

上面的实现就是一个双重校验锁实现的单例。可以看到`SynchronizedLazyImpl`中没有实现 set/get 重载方法，我们找下实际上是个拓展方法：

```
@kotlin.internal.InlineOnly
public inline operator fun <T> Lazy<T>.getValue(thisRef: Any?, property: KProperty<*>): T = value
```

#### observable

除了 by lazy 外，还有个有意思的 observable：

```kotlin
import kotlin.properties.Delegates

class User {
    var name: String by Delegates.observable("<no name>") {
        prop, old, new ->
        println("$old -> $new")
    }
}

fun main() {
    val user = User()
    user.name = "first"
    user.name = "second"
}

// 输出
<no name> -> first
first -> second
```

可以看到，就是隐藏了一下 set 重载的实现，我们点进去看下：

```kotlin
public inline fun <T> observable(initialValue: T, crossinline onChange: (property: KProperty<*>, oldValue: T, newValue: T) -> Unit):
        ReadWriteProperty<Any?, T> =
    object : ObservableProperty<T>(initialValue) {
        override fun afterChange(property: KProperty<*>, oldValue: T, newValue: T) = onChange(property, oldValue, newValue)
    }
```

可以看到，是一个 ObservableProperty 对象，内部也比较简单：

```kotlin
public abstract class ObservableProperty<V>(initialValue: V) : ReadWriteProperty<Any?, V> {
    private var value = initialValue

    /**
     *  The callback which is called before a change to the property value is attempted.
     *  The value of the property hasn't been changed yet, when this callback is invoked.
     *  If the callback returns `true` the value of the property is being set to the new value,
     *  and if the callback returns `false` the new value is discarded and the property remains its old value.
     */
    protected open fun beforeChange(property: KProperty<*>, oldValue: V, newValue: V): Boolean = true

    /**
     * The callback which is called after the change of the property is made. The value of the property
     * has already been changed when this callback is invoked.
     */
    protected open fun afterChange(property: KProperty<*>, oldValue: V, newValue: V): Unit {}

    public override fun getValue(thisRef: Any?, property: KProperty<*>): V {
        return value
    }

    public override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
        val oldValue = this.value
        if (!beforeChange(property, oldValue, value)) {
            return
        }
        this.value = value
      	// 这个就是我们设置值最终进入的地方
        afterChange(property, oldValue, value)
    }

    override fun toString(): String = "ObservableProperty(value=$value)"
}
```

可以看到其实现了 get 和 set 方法，但是不是操作符的，这是因为其继承了`ReadWriteProperty`接口：

```kotlin
public interface ReadWriteProperty<in T, V> : ReadOnlyProperty<T, V> {
    /**
     * Returns the value of the property for the given object.
     * @param thisRef the object for which the value is requested.
     * @param property the metadata for the property.
     * @return the property value.
     */
    public override operator fun getValue(thisRef: T, property: KProperty<*>): V

    /**
     * Sets the value of the property for the given object.
     * @param thisRef the object for which the value is requested.
     * @param property the metadata for the property.
     * @param value the value to set.
     */
    public operator fun setValue(thisRef: T, property: KProperty<*>, value: V)
}
```

### 代理给其他字段

属性可以委托其 getter 和 setter 给另一个属性。此种委托对于顶层属性和类属性（成员和扩展）都可用。委托属性可以是：

1. 顶层属性
2. 同一类的成员或扩展属性
3. 另一个类的成员或扩展属性

要将属性委托给另一个属性，需要在委托的名称中使用::限定符，例如this::delegate或MyClass::delegate：

```kotlin
var topLevelInt: Int = 0
class ClassWithDelegate(val anotherClassInt: Int)

class MyClass(var memberInt: Int, val anotherClassInstance: ClassWithDelegate) {
  	
  	// 
  	var delegatedToMember: Int by this::memberInt
  	
  	// 代理给了外部属性
    var delegatedToTopLevel: Int by ::topLevelInt

    val delegatedToAnotherClass: Int by anotherClassInstance::anotherClassInt
}
var MyClass.extDelegated: Int by ::topLevelInt
```

这个我们用得少，但其实某些场景下会很有用，例如，当想以**向后兼容的方式**重命名属性时：引入一个新属性，用@Deprecated注解标记旧属性，并委托其实现

```kotlin
class MyClass {
   var newName: Int = 0
   @Deprecated("Use 'newName' instead", ReplaceWith("newName"))
   var oldName: Int by this::newName
}
fun main() {
   val myClass = MyClass()
   // Notification: 'oldName: Int' is deprecated.
   // Use 'newName' instead
   myClass.oldName = 42
   println(myClass.newName) // 42
}
```

### 优化

代理的位置比较多，Kotlin 也提供了一些优化的方式，本质上就是为了减少编译器生成的`$delegate`字段：

```kotlin
class C {
    var prop: Type by MyDelegate()
}

// this code is generated by the compiler instead:
class C {
  	// 编译器会多生成一个 $delegate 对象
    private val prop$delegate = MyDelegate()
    var prop: Type
        get() = prop$delegate.getValue(this, this::prop)
        set(value: Type) = prop$delegate.setValue(this, this::prop, value)
}
```

详见[文档](https://kotlinlang.org/docs/delegated-properties.html#translation-rules-for-delegated-properties)

