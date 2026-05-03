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
import jakarta.servlet.http.HttpSession
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
        // CSRF disabled so MacroDroid and local forms work instantly
        http.csrf { it.disable() }.authorizeHttpRequests { it.anyRequest().permitAll() }
        return http.build()
    }
}

@Entity @Table(name="app_user")
class User(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    var username: String = "", var password: String = "", var score: Int = 0,
    var alarmMsg: String = "Break the loop. Choose Clarity.", var water: Int = 0
)
interface UserRepo : JpaRepository<User, Long> { fun findByUsername(username: String): User? }

@Controller
class MainController(val repo: UserRepo, val encoder: PasswordEncoder) {

    @GetMapping("/")
    fun index(session: HttpSession, model: Model): String {
        val userId = session.getAttribute("UID") as? Long ?: return "redirect:/login"
        val user = repo.findById(userId).orElse(null) ?: return "redirect:/signup"
        model.addAttribute("user", user)
        return "index"
    }

    @GetMapping("/login") fun lPage() = "login"
    @GetMapping("/signup") fun sPage() = "signup"

    @PostMapping("/signup")
    fun signup(@RequestParam username: String, @RequestParam password: String): String {
        if (repo.findByUsername(username) == null) {
            repo.save(User(username = username, password = encoder.encode(password)))
        }
        return "redirect:/login"
    }

    @PostMapping("/login")
    fun login(@RequestParam username: String, @RequestParam password: String, session: HttpSession): String {
        val user = repo.findByUsername(username)
        if (user != null && encoder.matches(password, user.password)) {
            session.setAttribute("UID", user.id)
            return "redirect:/"
        }
        return "redirect:/login?error"
    }

    @GetMapping("/logout") fun logout(session: HttpSession): String {
        session.invalidate()
        return "redirect:/login"
    }

    @GetMapping("/vault") fun vault() = "vault"
}

@RestController
@RequestMapping("/api")
class ClarityAPI(val repo: UserRepo) {
    // NATURAL API Webhook
    @PostMapping("/webhook")
    fun track(@RequestBody data: Map<String, String>): String {
        val u = repo.findAll().firstOrNull() ?: return "Sign up on web first!"
        val bad = data["app"] in listOf("Instagram", "TikTok", "Facebook", "Cooking Madness")
        u.score += if (bad) -1 else 1
        repo.save(u)
        return if (bad) u.alarmMsg else "Clarity maintained."
    }

    @GetMapping("/status")
    fun getStatus(): Map<String, Int> {
        val u = repo.findAll().firstOrNull() ?: return mapOf()
        return mapOf("score" to u.score, "water" to u.water)
    }
}