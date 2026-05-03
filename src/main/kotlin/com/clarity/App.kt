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
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.nio.file.*

@SpringBootApplication
class ClarityApplication
fun main(args: Array<String>) { runApplication<ClarityApplication>(*args) }

@Configuration
class SecurityConfig {
    @Bean fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
    @Bean fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http.csrf { it.disable() }
            .authorizeHttpRequests {
                it.requestMatchers("/signup", "/webhook", "/login", "/static/**").permitAll()
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
    var alarmMsg: String = "Stop scrolling. Focus!", var water: Int = 0, var studySeconds: Long = 0
)

interface UserRepo : JpaRepository<User, Long> { fun findByUsername(username: String): User? }

@Controller
class MasterController(val repo: UserRepo, val encoder: PasswordEncoder) {

    @GetMapping("/")
    fun home(model: Model): String {
        // Find ANY user in the DB (usually just you)
        val user = repo.findAll().firstOrNull() ?: return "redirect:/signup"
        model.addAttribute("user", user)
        return "index"
    }

    @GetMapping("/signup") fun signupP() = "signup"
    @GetMapping("/login") fun loginP() = "login"

    @PostMapping("/signup")
    fun signup(@RequestParam username: String, @RequestParam password: String): String {
        if (repo.findByUsername(username) == null) {
            repo.save(User(username = username, password = encoder.encode(password)))
        }
        return "redirect:/login"
    }
    
    // Feature Routes
    @PostMapping("/water/add")
    fun addWater(): String {
        val u = repo.findAll().firstOrNull() ?: return "redirect:/signup"
        u.water += 250; repo.save(u); return "redirect:/"
    }

    @PostMapping("/study/save")
    fun saveStudy(@RequestParam seconds: Long): String {
        val u = repo.findAll().firstOrNull() ?: return "redirect:/signup"
        u.studySeconds += seconds; repo.save(u); return "redirect:/"
    }
    
    @GetMapping("/recorder") fun recorder() = "recorder"
    @GetMapping("/vault") fun vault() = "vault"
}

@RestController
class API(val repo: UserRepo) {
    @PostMapping("/webhook")
    fun track(@RequestBody data: Map<String, String>): String {
        val u = repo.findAll().firstOrNull() ?: return "No User Setup"
        val distractions = listOf("Instagram", "TikTok", "Facebook", "Snapchat", "Cooking Madness")
        val impact = if (data["app"] in distractions) -1 else 1
        u.score += impact; repo.save(u)
        return if (impact < 0) u.alarmMsg else "Maintain focus."
    }
}