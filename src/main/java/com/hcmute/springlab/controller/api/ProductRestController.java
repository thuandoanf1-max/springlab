package com.hcmute.springlab.controller.api;

import com.hcmute.springlab.dto.ProductResponse;
import com.hcmute.springlab.entity.Category;
import com.hcmute.springlab.entity.Product;
import com.hcmute.springlab.service.CategoryService;
import com.hcmute.springlab.service.ProductService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/admin/api/products")
public class ProductRestController {

    private static final String UPLOAD_DIR = "uploads/";

    private final ProductService productService;
    private final CategoryService categoryService;

    public ProductRestController(ProductService productService, CategoryService categoryService) {
        this.productService = productService;
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<ProductResponse> findAll() {
        return productService.findAll().stream().map(ProductResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> findById(@PathVariable Long id) {
        return productService.findById(id)
                .map(product -> ResponseEntity.ok(ProductResponse.from(product)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<?> create(
            @RequestParam("name") @NotBlank(message = "Product name is required")
            @Size(max = 100, message = "Name must be less than 100 characters") String name,
            @RequestParam("quantity") @NotNull(message = "Quantity is required")
            @PositiveOrZero(message = "Quantity must not be negative") Integer quantity,
            @RequestParam("price") @NotNull(message = "Price is required")
            @PositiveOrZero(message = "Price must not be negative") Double price,
            @RequestParam("categoryId") @NotNull(message = "Category is required") Long categoryId,
            @RequestParam(value = "image", required = false) MultipartFile image) throws IOException {

        Optional<Category> category = categoryService.findById(categoryId);
        if (category.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "Category does not exist");
        }

        Product product = new Product();
        product.setName(name.trim());
        product.setQuantity(quantity);
        product.setPrice(price);
        product.setCategory(category.get());
        if (image != null && !image.isEmpty()) {
            product.setImage(saveImage(image));
        }

        Product savedProduct = productService.createForCurrentUser(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductResponse.from(savedProduct));
    }

    @PutMapping(value = "/{id}", consumes = "multipart/form-data")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @RequestParam("name") @NotBlank(message = "Product name is required")
            @Size(max = 100, message = "Name must be less than 100 characters") String name,
            @RequestParam("quantity") @NotNull(message = "Quantity is required")
            @PositiveOrZero(message = "Quantity must not be negative") Integer quantity,
            @RequestParam("price") @NotNull(message = "Price is required")
            @PositiveOrZero(message = "Price must not be negative") Double price,
            @RequestParam("categoryId") @NotNull(message = "Category is required") Long categoryId,
            @RequestParam(value = "image", required = false) MultipartFile image) throws IOException {

        Optional<Product> existingProduct = productService.findById(id);
        if (existingProduct.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Optional<Category> category = categoryService.findById(categoryId);
        if (category.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "Category does not exist");
        }

        Product product = existingProduct.get();
        product.setName(name.trim());
        product.setQuantity(quantity);
        product.setPrice(price);
        product.setCategory(category.get());
        if (image != null && !image.isEmpty()) {
            product.setImage(saveImage(image));
        }

        return ResponseEntity.ok(ProductResponse.from(productService.save(product)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (productService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        productService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleValidationError(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("Invalid product data");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, String>> handleUploadError(IOException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Could not save the product image"));
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
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
