package org.learningjava.bmtool1.infrastructure.adapter.in.web.admin;

import org.learningjava.bmtool1.domain.service.prompting.PromptingTechnique;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/prompting")
public class PromptingOptionsController {

    @GetMapping("/options")
    public PromptingTechnique[] options() {
        return PromptingTechnique.values();
    }
}
