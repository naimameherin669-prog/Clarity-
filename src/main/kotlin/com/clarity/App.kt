package com.clarity

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.*
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository

@SpringBootApplication
class ClarityApplication

fun main(args: Array<String>) {
    runApplication<ClarityApplication>(*args)
}

@Entity
class User(@Id @GeneratedValue var id: Long? = null, var score: Int = 0)
interface UserRepo : JpaRepository<User, Long>

@RestController
class Webhook(val repo: UserRepo) {
    @PostMapping("/webhook")
    fun track(@RequestBody data: Map<String, String>): String {
        val user = repo.findAll().firstOrNull() ?: repo.save(User())
        val distractions = listOf("Instagram", "TikTok", "Facebook", "Snapchat")
        val impact = if (data["app"] in distractions) -1 else 1
        user.score += impact
        repo.save(user)
        return if (impact < 0) "The fog returns. Choose Clarity." else "Focus maintained."
    }
}

@Controller
class Dashboard(val repo: UserRepo) {
    @GetMapping("/")
    fun home(model: Model): String {
        val user = repo.findAll().firstOrNull() ?: repo.save(User())
        model.addAttribute("score", user.score)
        return "index"
    }
}
