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
        http.csrf { it.disable() }.authorizeHttpRequests {
            it.requestMatchers("/signup", "/webhook", "/login", "/css/**").permitAll().anyRequest().authenticated()
        }.formLogin { it.loginPage("/login").defaultSuccessUrl("/", true).permitAll() }
        return http.build()
    }
}

// --- DATA ---
@Entity @Table(name="app_user")
class User(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    val username: String = "", val password: String = "", var score: Int = 0,
    var alarmMsg: String = "Wake up!", var alarmMusic: String = ""
)
interface UserRepo : JpaRepository<User, Long> { fun findByUsername(username: String): User? }

// --- LOGIC ---
@RestController
class Webhook(val repo: UserRepo) {
    @PostMapping("/webhook")
    fun track(@RequestBody data: Map<String, String>): String {
        val user = repo.findAll().firstOrNull() ?: return "No User"
        val distractions = listOf("Instagram", "TikTok", "Facebook", "Snapchat", "Cooking Madness")
        user.score += if (data["app"] in distractions) -1 else 1
        repo.save(user)
        return user.alarmMsg // Returns your custom message to MacroDroid!
    }
}

@Controller
class FullAppController(val repo: UserRepo, val encoder: PasswordEncoder) {
    @GetMapping("/")
    fun home(model: Model): String {
        val user = repo.findAll().firstOrNull() ?: return "redirect:/signup"
        model.addAttribute("user", user)
        return "index"
    }

    @GetMapping("/vault") fun vault() = "vault"
    @GetMapping("/alarm") fun alarm(model: Model): String {
        model.addAttribute("user", repo.findAll().first()[0])
        return "alarm"
    }

    @PostMapping("/upload")
    fun handleUpload(@RequestParam("file") file: MultipartFile): String {
        val root = Paths.get("static/uploads")
        if (!Files.exists(root)) Files.createDirectories(root)
        Files.copy(file.inputStream, root.resolve(file.originalFilename ?: "file"))
        return "redirect:/vault"
    }

    @GetMapping("/signup") fun signupPage() = "signup"
    @PostMapping("/signup")
    fun signup(@RequestParam username: String, @RequestParam password: String): String {
        if (repo.findByUsername(username) == null) repo.save(User(username = username, password = encoder.encode(password)))
        return "redirect:/login"
    }
    @GetMapping("/login") fun loginPage() = "login"
}