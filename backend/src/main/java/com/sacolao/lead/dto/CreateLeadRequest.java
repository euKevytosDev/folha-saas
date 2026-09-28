package com.sacolao.lead.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLeadRequest(
        @NotBlank(message = "Informe seu nome")
        @Size(min = 2, max = 120, message = "O nome precisa ter entre 2 e 120 caracteres")
        String name,
        @NotBlank(message = "Informe o WhatsApp")
        @Size(max = 32, message = "WhatsApp muito longo")
        String phone,
        @NotBlank(message = "Informe o nome do comércio")
        @Size(min = 2, max = 160, message = "O nome do comércio precisa ter entre 2 e 160 caracteres")
        String businessName,
        @NotBlank(message = "Conte em poucas palavras como é o comércio e o que você quer")
        @Size(min = 12, max = 500, message = "Use pelo menos uma frase curta, com no máximo 500 caracteres")
        String brief,
        @Size(max = 200)
        String website
) {
}
