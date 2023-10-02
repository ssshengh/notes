import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.ss.example.Cat
import com.ss.example.Dog
import java.io.FileInputStream
import java.lang.reflect.Type

fun main() {
    // 文件路径
    val path = "./src/main/resources/bean1.json"
    val inputStream = FileInputStream(path)

    // json 读取器
    val om = ObjectMapper()
    val beanDefinition = om.readValue(inputStream, object : TypeReference<List<BeanDefinition>>() {
        override fun getType(): Type {
            return super.getType()
        }
    })

    // 读取并装载容器
    val beanFactory = mutableMapOf<String, Any>()
    beanDefinition.forEach {
        val id = it.id
        val clazz = it.clazz
        println(clazz)
        Class.forName(clazz).getConstructor().newInstance().also { bean ->
            beanFactory[id!!] = bean
        }
    }

    // 从容器中获取对象
    val bean1 = beanFactory["dog"] as Dog
    val bean2 = beanFactory["cat"] as Cat
    println(bean1)
    println(bean2)

}