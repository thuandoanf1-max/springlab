package com.hcmute.springlab.controller;

import com.hcmute.springlab.entity.Category;
import com.hcmute.springlab.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/admin/categories")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    private static final String UPLOAD_DIR = "uploads/";

    @GetMapping
    public String list(@RequestParam(required = false) String keyword, Model model) {
        model.addAttribute("categories", categoryService.searchByName(keyword));
        model.addAttribute("keyword", keyword);
        return "admin/category/list";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("category", new Category());
        return "admin/category/form";
    }

    @PostMapping("/create")
    public String create(@Valid @ModelAttribute("category") Category category, BindingResult result, @RequestParam("imageFile") MultipartFile imageFile) {
        if (result.hasErrors()) {
            return "admin/category/form";
        }
        try {
            if (!imageFile.isEmpty()) {
                String fileName = saveImage(imageFile);
                category.setImage(fileName);
            }
            categoryService.save(category);
        } catch (Exception e) {
            result.rejectValue("name", "error.category", "Error: " + e.getMessage());
            return "admin/category/form";
        }
        return "redirect:/admin/categories";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        Optional<Category> category = categoryService.findById(id);
        if (category.isPresent()) {
            model.addAttribute("category", category.get());
            return "admin/category/form";
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/edit/{id}")
    public String edit(@PathVariable Long id, @Valid @ModelAttribute("category") Category category, BindingResult result, @RequestParam("imageFile") MultipartFile imageFile) {
        if (result.hasErrors()) {
            return "admin/category/form";
        }
        category.setId(id);
        try {
            Optional<Category> existingCategory = categoryService.findById(id);
            if (existingCategory.isPresent()) {
                category.setImage(existingCategory.get().getImage()); // keep old image by default
            }

            if (!imageFile.isEmpty()) {
                String fileName = saveImage(imageFile);
                category.setImage(fileName);
            }
            categoryService.save(category);
        } catch (Exception e) {
            result.rejectValue("name", "error.category", "Error: " + e.getMessage());
            return "admin/category/form";
        }
        return "redirect:/admin/categories";
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        categoryService.deleteById(id);
        return "redirect:/admin/categories";
    }

    private String saveImage(MultipartFile imageFile) throws IOException {
        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }
        String fileName = UUID.randomUUID().toString() + "_" + imageFile.getOriginalFilename();
        Path filePath = Paths.get(UPLOAD_DIR, fileName);
        Files.copy(imageFile.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        return fileName;
    }
}
