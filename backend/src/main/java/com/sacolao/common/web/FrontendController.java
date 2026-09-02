package com.sacolao.common.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendController {

    @GetMapping("/")
    public String home() {
        return "forward:/pages/index.html";
    }

    @GetMapping("/login")
    public String login() {
        return "forward:/pages/login.html";
    }

    @GetMapping("/cadastro")
    public String signup() {
        return "forward:/pages/cadastro.html";
    }

    @GetMapping("/recuperar-senha")
    public String recoverPassword() {
        return "forward:/pages/recuperar-senha.html";
    }

    @GetMapping("/redefinir-senha")
    public String resetPassword() {
        return "forward:/pages/redefinir-senha.html";
    }

    @GetMapping("/loja/{slug}")
    public String store() {
        return "forward:/pages/loja.html";
    }

    @GetMapping("/admin")
    public String admin() {
        return "forward:/pages/admin.html";
    }

    @GetMapping("/superadmin")
    public String superAdmin() {
        return "forward:/pages/superadmin.html";
    }
}
