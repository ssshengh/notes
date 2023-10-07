import com.ss.example.User
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.support.FileSystemXmlApplicationContext

fun main(args: Array<String>) {
    val ctx = FileSystemXmlApplicationContext("src/main/resources/beans.xml")
    val bean = ctx.getBean("user", User::class.java) as User
    println(bean)

    AnnotationConfigApplicationContext(BeanConfig::class.java).use { ctx ->
        val bean2 = ctx.getBean("user", User::class.java) as User
        println(bean2)
    }
}