package com.clarity

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.*
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.bind.annotation.*
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@SpringBootApplication
class ClarityApplication
fun main(args: Array<String>) { runApplication<ClarityApplication>(*args) }

@Configuration
class SecurityConfig {
    @Bean fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
    @Bean fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http.csrf { it.disable() }
            .authorizeHttpRequests {
                // Wide-open permissions for Auth and API
                it.requestMatchers("/", "/signup", "/login", "/api/**", "/static/**").permitAll()
                it.anyRequest().authenticated()
            }
            .formLogin { it.loginPage("/login").defaultSuccessUrl("/", true).permitAll() }
            .logout { it.logoutSuccessUrl("/login") }
        return http.build()
    }
}

@Entity @Table(name="app_user")
class User(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    val username: String = "", val password: String = "", var score: Int = 0,
    var alarmMsg: String = "The fog returns. Choose Clarity.", var water: Int = 0
)
interface UserRepo : JpaRepository<User, Long> { fun findByUsername(username: String): User? }

@Controller
class WebController(val repo: UserRepo, val encoder: PasswordEncoder) {
    @GetMapping("/") fun home(model: Model): String {
        val u = repo.findAll().firstOrNull() ?: return "redirect:/signup"
        model.addAttribute("user", u)
        return "index"
    }
    @GetMapping("/signup") fun sP() = "signup"
    @GetMapping("/login") fun lP() = "login"
    @PostMapping("/signup") fun s(@RequestParam username: String, @RequestParam password: String): String {
        if (repo.findByUsername(username) == null) {
            repo.save(User(username = username, password = encoder.encode(password)))
        }
        return "redirect:/login"
    }
}

// --- THE NATURAL API (This is how you use the app independently) ---
@RestController
@RequestMapping("/api")
class ClarityAPI(val repo: UserRepo) {

    // 1. Natural Data Access (Open this in your browser to see your stats as JSON)
    @GetMapping("/status")
    fun getStatus(): Map<String, Any> {
        val u = repo.findAll().firstOrNull() ?: return mapOf("error" to "No User")
        return mapOf("score" to u.score, "water" to u.water, "identity" to u.username)
    }

    // 2. The Focus Webhook for MacroDroid
    @PostMapping("/webhook")
    fun receiveHook(@RequestBody data: Map<String, String>): Map<String, String> {
        val user = repo.findAll().firstOrNull() ?: return mapOf("msg" to "No User")
        val app = data["app"] ?: "Unknown"
        val badApps = listOf("Instagram", "TikTok", "Facebook", "Cooking Madness")
        
        if (app in badApps) {
            user.score -= 1
            repo.save(user)
            return mapOf("response" to user.alarmMsg) // Returns your custom insult
        }
        user.score += 1
        repo.save(user)
        return mapOf("response" to "Focus Maintained.")
    }
}