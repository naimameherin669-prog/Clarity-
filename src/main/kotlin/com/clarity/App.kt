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
import org.springframework.web.multipart.MultipartFile
import java.nio.file.*

@SpringBootApplication
class ClarityApplication
fun main(args: Array<String>) { runApplication<ClarityApplication>(*args) }

@Configuration
class SecurityConfig {
    @Bean fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
    @Bean fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http.csrf { it.disable() }.headers { it.frameOptions { it.disable() } }
            .authorizeHttpRequests {
                it.requestMatchers("/", "/signup", "/login", "/api/**", "/static/**").permitAll()
                it.anyRequest().authenticated()
            }
            .formLogin { it.disable() } // Using custom manual login to stop loops
        return http.build()
    }
}

// --- PERMANENT FEATURES DATA MODEL ---
@Entity @Table(name="app_user")
class User(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    var username: String = "", var password: String = "", var score: Int = 0,
    var water: Int = 0, var studySeconds: Long = 0, var alarmMsg: String = "Break the loop. Choose Clarity."
)
interface UserRepo : JpaRepository<User, Long> { fun findByUsername(u: String): User? }

@Controller
class MainController(val repo: UserRepo, val encoder: PasswordEncoder) {

    // LOGS IN IMMEDIATELY IF ACCOUNT EXISTS
    @GetMapping("/")
    fun index(session: HttpSession, model: Model): String {
        val uid = session.getAttribute("UID") as? Long ?: return "redirect:/login"
        val user = repo.findById(uid).orElse(null) ?: return "redirect:/signup"
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
        val u = repo.findByUsername(username)
        if (u != null && encoder.matches(password, u.password)) {
            session.setAttribute("UID", u.id) // SUCCESSFUL LOGIN
            return "redirect:/"
        }
        return "redirect:/login?error"
    }

    @PostMapping("/water/add")
    fun addWater(session: HttpSession): String {
        val uid = session.getAttribute("UID") as? Long ?: return "redirect:/login"
        val u = repo.findById(uid).get(); u.water += 250; repo.save(u)
        return "redirect:/"
    }

    @PostMapping("/study/save")
    fun saveStudy(@RequestParam seconds: Long, session: HttpSession): String {
        val uid = session.getAttribute("UID") as? Long ?: return "redirect:/login"
        val u = repo.findById(uid).get(); u.studySeconds += seconds; repo.save(u)
        return "redirect:/"
    }

    @GetMapping("/logout") fun logout(session: HttpSession): String {
        session.invalidate()
        return "redirect:/login"
    }

    // High-Res Feature Links
    @GetMapping("/recorder") fun r() = "recorder"
    @GetMapping("/vault") fun v(model: Model) = repo.findAll().firstOrNull().let { model.addAttribute("user", it); "vault" }
}

@RestController
@RequestMapping("/api")
class API(val repo: UserRepo) {
    @PostMapping("/webhook")
    fun track(@RequestBody data: Map<String, String>): String {
        val u = repo.findAll().firstOrNull() ?: return "Sign up via browser first"
        val isBad = data["app"] in listOf("Instagram", "TikTok", "Facebook", "Snapchat", "Cooking Madness")
        u.score += if (isBad) -1 else 1
        repo.save(u)
        return if (isBad) u.alarmMsg else "Clarity maintained."
    }

    @GetMapping("/status")
    fun stats() = repo.findAll().firstOrNull()?.let { mapOf("score" to it.score, "water" to it.water) } ?: mapOf("msg" to "No User")
}