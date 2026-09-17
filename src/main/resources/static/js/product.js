(() => {
    const productsApiUrl = '/admin/api/products';
    const categoriesApiUrl = '/admin/api/categories';
    const tableBody = document.querySelector('#productTableBody');
    const emptyMessage = document.querySelector('#emptyProductMessage');
    const alertBox = document.querySelector('#productAlert');
    const form = document.querySelector('#productForm');
    const modal = new bootstrap.Modal(document.querySelector('#productModal'));
    const modalTitle = document.querySelector('#productModalLabel');
    const submitButton = document.querySelector('#productSubmitButton');
    const idInput = document.querySelector('#productId');
    const nameInput = document.querySelector('#productName');
    const quantityInput = document.querySelector('#productQuantity');
    const priceInput = document.querySelector('#productPrice');
    const categorySelect = document.querySelector('#productCategory');
    const imageHint = document.querySelector('#currentProductImageHint');
    const imageInput = document.querySelector('#productImage');
    const imagePreview = document.querySelector('#productImagePreview');
    const productCount = document.querySelector('#productCount');

    function showMessage(message, type = 'danger') {
        alertBox.textContent = message;
        alertBox.className = `alert alert-${type}`;
        alertBox.classList.remove('d-none');
    }

    function clearMessage() {
        alertBox.textContent = '';
        alertBox.classList.add('d-none');
    }

    async function readError(response) {
        try {
            const data = await response.json();
            return data.error || data.message || 'The request could not be completed.';
        } catch (_) {
            return 'The request could not be completed.';
        }
    }

    function appendCell(row, value) {
        const cell = document.createElement('td');
        cell.textContent = value ?? '';
        row.appendChild(cell);
    }

    function imageCell(imageName) {
        const cell = document.createElement('td');
        if (imageName) {
            const image = document.createElement('img');
            image.src = `/uploads/${encodeURIComponent(imageName)}`;
            image.alt = 'Product image';
            image.width = 50;
            image.height = 50;
            image.className = 'object-fit-cover';
            cell.appendChild(image);
        }
        return cell;
    }

    function actionsCell(product) {
        const cell = document.createElement('td');
        cell.className = 'text-end';
        const editButton = document.createElement('button');
        editButton.type = 'button';
        editButton.className = 'btn btn-sm btn-outline-primary me-2';
        editButton.innerHTML = '<i class="bi bi-pencil"></i> Edit';
        editButton.title = 'Edit product';
        editButton.addEventListener('click', () => openEditModal(product.id));

        const deleteButton = document.createElement('button');
        deleteButton.type = 'button';
        deleteButton.className = 'btn btn-sm btn-outline-danger';
        deleteButton.innerHTML = '<i class="bi bi-trash"></i> Delete';
        deleteButton.title = 'Delete product';
        deleteButton.addEventListener('click', () => deleteProduct(product.id));

        cell.append(editButton, deleteButton);
        return cell;
    }

    function quantityCell(quantity) {
        const cell = document.createElement('td');
        const badge = document.createElement('span');
        badge.className = 'stat-pill';
        badge.textContent = quantity ?? 0;
        cell.appendChild(badge);
        return cell;
    }

    function priceCell(price) {
        const cell = document.createElement('td');
        cell.className = 'fw-semibold';
        cell.textContent = new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(price ?? 0);
        return cell;
    }

    function categoryCell(categoryName) {
        const cell = document.createElement('td');
        const badge = document.createElement('span');
        badge.className = 'role-badge role-user';
        badge.textContent = categoryName ?? '';
        cell.appendChild(badge);
        return cell;
    }

    function renderProducts(products) {
        tableBody.replaceChildren();
        productCount.textContent = products.length;
        emptyMessage.classList.toggle('d-none', products.length !== 0);

        products.forEach((product) => {
            const row = document.createElement('tr');
            appendCell(row, product.id);
            row.appendChild(imageCell(product.image));
            appendCell(row, product.name);
            row.appendChild(quantityCell(product.quantity));
            row.appendChild(priceCell(product.price));
            row.appendChild(categoryCell(product.categoryName));
            row.appendChild(actionsCell(product));
            tableBody.appendChild(row);
        });
    }

    async function loadProducts() {
        try {
            const response = await fetch(productsApiUrl);
            if (!response.ok) {
                throw new Error(await readError(response));
            }
            renderProducts(await response.json());
        } catch (error) {
            renderProducts([]);
            showMessage(error.message || 'Network error while loading products.');
        }
    }

    async function loadCategories(selectedId = '') {
        const response = await fetch(categoriesApiUrl);
        if (!response.ok) {
            throw new Error(await readError(response));
        }
        const categories = await response.json();
        categorySelect.replaceChildren();

        const placeholder = document.createElement('option');
        placeholder.value = '';
        placeholder.textContent = 'Select a category';
        placeholder.disabled = true;
        placeholder.selected = !selectedId;
        categorySelect.appendChild(placeholder);

        categories.forEach((category) => {
            const option = document.createElement('option');
            option.value = category.id;
            option.textContent = category.name;
            option.selected = String(category.id) === String(selectedId);
            categorySelect.appendChild(option);
        });
    }

    function resetForm() {
        form.reset();
        idInput.value = '';
        imageHint.textContent = '';
        imageHint.classList.add('d-none');
        imagePreview.removeAttribute('src');
        imagePreview.classList.add('d-none');
    }

    document.querySelector('#addProductButton').addEventListener('click', async () => {
        clearMessage();
        resetForm();
        try {
            await loadCategories();
            modalTitle.textContent = 'Add Product';
            submitButton.textContent = 'Add Product';
            modal.show();
        } catch (error) {
            showMessage(error.message || 'Network error while loading categories.');
        }
    });

    async function openEditModal(id) {
        clearMessage();
        try {
            const response = await fetch(`${productsApiUrl}/${id}`);
            if (!response.ok) {
                throw new Error(await readError(response));
            }
            const product = await response.json();
            resetForm();
            await loadCategories(product.categoryId);
            idInput.value = product.id;
            nameInput.value = product.name || '';
            quantityInput.value = product.quantity ?? '';
            priceInput.value = product.price ?? '';
            if (product.image) {
                imageHint.textContent = `Current image: ${product.image}. Leave image empty to keep it.`;
                imageHint.classList.remove('d-none');
                imagePreview.src = `/uploads/${encodeURIComponent(product.image)}`;
                imagePreview.classList.remove('d-none');
            }
            modalTitle.textContent = 'Edit Product';
            submitButton.textContent = 'Save Changes';
            modal.show();
        } catch (error) {
            showMessage(error.message || 'Network error while loading the product.');
        }
    }

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        clearMessage();
        submitButton.disabled = true;
        const id = idInput.value;

        try {
            const response = await fetch(id ? `${productsApiUrl}/${id}` : productsApiUrl, {
                method: id ? 'PUT' : 'POST',
                body: new FormData(form)
            });
            if (!response.ok) {
                throw new Error(await readError(response));
            }
            modal.hide();
            resetForm();
            await loadProducts();
            showMessage(id ? 'Product updated successfully.' : 'Product added successfully.', 'success');
        } catch (error) {
            showMessage(error.message || 'Network error while saving the product.');
        } finally {
            submitButton.disabled = false;
        }
    });

    imageInput.addEventListener('change', () => {
        const [file] = imageInput.files;
        if (!file) {
            return;
        }
        imagePreview.src = URL.createObjectURL(file);
        imagePreview.classList.remove('d-none');
    });

    async function deleteProduct(id) {
        if (!window.confirm('Are you sure you want to delete this product?')) {
            return;
        }
        clearMessage();
        try {
            const response = await fetch(`${productsApiUrl}/${id}`, { method: 'DELETE' });
            if (!response.ok) {
                throw new Error(await readError(response));
            }
            await loadProducts();
            showMessage('Product deleted successfully.', 'success');
        } catch (error) {
            showMessage(error.message || 'Network error while deleting the product.');
        }
    }

    loadProducts();
})();
