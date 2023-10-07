import com.ss.example.Cat
import com.ss.example.User
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Scope


class BeanConfig {

    /**
     * @Bean 注解相当于在 xml 中配置了一个 bean，方法名相当于 bean 的 id，返回值相当于 bean的 class
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

    @Bean
    fun cat(): Cat {
        val cat = Cat()
        cat.name = "小黑"
        cat.age = 2
        cat.weight = 3.5
        return cat
    }
}