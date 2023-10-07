import com.ss.example.Dog
import org.springframework.context.support.ClassPathXmlApplicationContext

fun main(args: Array<String>) {
    // 这个类会去 resource 目录下找 beans.xml 文件, 他会加载 Spring 文件, 并初始化容器
    // 这意味着, 哪怕不执行, Dog 对象也已经存在了
    val context = ClassPathXmlApplicationContext("set_properties.xml")

    val user1 = context.getBean("user") as com.ss.example.User
    println(user1)
}