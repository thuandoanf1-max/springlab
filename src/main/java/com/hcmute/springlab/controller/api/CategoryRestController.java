package com.hcmute.springlab.controller.api;

import com.hcmute.springlab.entity.Category;
import com.hcmute.springlab.service.CategoryService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/admin/api/categories")
public class CategoryRestController {

    private static final String UPLOAD_DIR = "uploads/";

    private final CategoryService categoryService;

    public CategoryRestController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<Category> findAll() {
        return categoryService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Category> findById(@PathVariable Long id) {
        return categoryService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Category> create(
            @RequestParam("name") @NotBlank(message = "Category name is required")
            @Size(max = 100, message = "Name must be less than 100 characters") String name,
            @RequestParam(value = "description", required = false) @Size(max = 500, message = "Description must be less than 500 characters") String description,
            @RequestParam(value = "image", required = false) MultipartFile image) throws IOException {

        Category category = new Category();
        category.setName(name.trim());
        category.setDescription(description);
        if (image != null && !image.isEmpty()) {
            category.setImage(saveImage(image));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.save(category));
    }

    @PutMapping(value = "/{id}", consumes = "multipart/form-data")
    public ResponseEntity<Category> update(
            @PathVariable Long id,
            @RequestParam("name") @NotBlank(message = "Category name is required")
            @Size(max = 100, message = "Name must be less than 100 characters") String name,
            @RequestParam(value = "description", required = false) @Size(max = 500, message = "Description must be less than 500 characters") String description,
            @RequestParam(value = "image", required = false) MultipartFile image) throws IOException {

        Optional<Category> existingCategory = categoryService.findById(id);
        if (existingCategory.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Category category = existingCategory.get();
        category.setName(name.trim());
        category.setDescription(description);
        if (image != null && !image.isEmpty()) {
            category.setImage(saveImage(image));
        }

        return ResponseEntity.ok(categoryService.save(category));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (categoryService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        categoryService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleValidationError(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("Invalid category data");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateName(DataIntegrityViolationException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", "Category name already exists"));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, String>> handleUploadError(IOException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Could not save the category image"));
    }

    private String saveImage(MultipartFile imageFile) throws IOException {
        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        String originalFilename = imageFile.getOriginalFilename();
        String fileName = UUID.randomUUID() + "_" + (originalFilename == null ? "image" : originalFilename);
        Path filePath = Paths.get(UPLOAD_DIR, fileName);
        Files.copy(imageFile.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        return fileName;
    }
}
