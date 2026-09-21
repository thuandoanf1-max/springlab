package com.hcmute.springlab.controller;

import com.hcmute.springlab.dto.UserFormRequest;
import com.hcmute.springlab.mapper.UserMapper;
import com.hcmute.springlab.service.ProductService;
import com.hcmute.springlab.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;
    private final ProductService productService;

    public UserController(UserService userService, UserMapper userMapper, ProductService productService) {
        this.userService = userService;
        this.userMapper = userMapper;
        this.productService = productService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(required = false) String keyword,
                       Model model) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        var pageable = PageRequest.of(safePage, safeSize, Sort.by("id").ascending());
        var userPage = userService.search(keyword, pageable).map(userMapper::toResponse);
        model.addAttribute("userPage", userPage);
        model.addAttribute("users", userPage.getContent());
        model.addAttribute("keyword", keyword == null ? "" : keyword.trim());
        model.addAttribute("size", safeSize);
        model.addAttribute("totalUsers", userService.count());
        model.addAttribute("productCounts", productService.countByUserIds(
                userPage.getContent().stream().map(user -> user.id()).toList()));
        return "admin/user/list";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("user", new UserFormRequest());
        return "admin/user/form";
    }

    @PostMapping("/create")
    public String create(@Valid @ModelAttribute("user") UserFormRequest user, BindingResult result) {
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            result.rejectValue("password", "error.user", "Password is required.");
        }
        if (result.hasErrors()) {
            return "admin/user/form";
        }
        try {
            userService.save(userMapper.toEntity(user));
        } catch (IllegalArgumentException e) {
            result.reject("error.user", e.getMessage());
            return "admin/user/form";
        }
        return "redirect:/admin/users";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        var user = userService.findById(id);
        if (user.isPresent()) {
            model.addAttribute("user", userMapper.toFormRequest(user.get()));
            return "admin/user/form";
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/edit/{id}")
    public String edit(@PathVariable Long id, @Valid @ModelAttribute("user") UserFormRequest user, BindingResult result) {
        if (result.hasErrors()) {
            return "admin/user/form";
        }
        user.setId(id);
        try {
            userService.save(userMapper.toEntity(user));
        } catch (IllegalArgumentException e) {
            result.reject("error.user", e.getMessage());
            return "admin/user/form";
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            userService.deleteById(id, authentication == null ? null : authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage", "User deleted successfully");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }
}
