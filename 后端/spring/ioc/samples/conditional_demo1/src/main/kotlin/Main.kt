import com.ss.example.DataSource
import com.ss.example.JavaConfig
import com.ss.example.ShowFileCmd
import org.springframework.context.annotation.AnnotationConfigApplicationContext

fun main(args: Array<String>) {
    // 不添加配置类，使得不调用 register() 方法，不初始化容器，不注册 bean
    val ctx = AnnotationConfigApplicationContext()
    // 设置环境
    ctx.environment.addActiveProfile("dev")
    ctx.environment.addActiveProfile("prod")
    // 注册配置类
    ctx.register(JavaConfig::class.java)
    // 刷新容器
    ctx.refresh()

    val dt = ctx.getBean("devDataSource", DataSource::class.java)
    println(dt.url)
}