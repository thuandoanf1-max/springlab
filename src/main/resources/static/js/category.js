(() => {
    const apiUrl = '/admin/api/categories';
    const tableBody = document.querySelector('#categoryTableBody');
    const emptyMessage = document.querySelector('#emptyMessage');
    const alertBox = document.querySelector('#categoryAlert');
    const form = document.querySelector('#categoryForm');
    const modalElement = document.querySelector('#categoryModal');
    const categoryModal = new bootstrap.Modal(modalElement);
    const modalTitle = document.querySelector('#categoryModalLabel');
    const submitButton = document.querySelector('#categorySubmitButton');
    const imageHint = document.querySelector('#currentImageHint');
    const imagePreview = document.querySelector('#categoryImagePreview');
    const categoryCount = document.querySelector('#categoryCount');
    const idInput = document.querySelector('#categoryId');
    const nameInput = document.querySelector('#categoryName');
    const descriptionInput = document.querySelector('#categoryDescription');
    const imageInput = document.querySelector('#categoryImage');

    function showMessage(message, type = 'danger') {
        alertBox.textContent = message;
        alertBox.className = `alert alert-${type}`;
        alertBox.classList.remove('d-none');
    }

    function clearMessage() {
        alertBox.classList.add('d-none');
        alertBox.textContent = '';
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

    function createImageCell(imageName) {
        const cell = document.createElement('td');
        if (imageName) {
            const image = document.createElement('img');
            image.src = `/uploads/${encodeURIComponent(imageName)}`;
            image.alt = 'Category image';
            image.width = 50;
            image.height = 50;
            image.className = 'object-fit-cover';
            cell.appendChild(image);
        }
        return cell;
    }

    function createActionsCell(category) {
        const cell = document.createElement('td');
        cell.className = 'text-end';
        const editButton = document.createElement('button');
        editButton.type = 'button';
        editButton.className = 'btn btn-sm btn-outline-primary me-2';
        editButton.innerHTML = '<i class="bi bi-pencil"></i> Edit';
        editButton.title = 'Edit category';
        editButton.addEventListener('click', () => openEditModal(category.id));

        const deleteButton = document.createElement('button');
        deleteButton.type = 'button';
        deleteButton.className = 'btn btn-sm btn-outline-danger';
        deleteButton.innerHTML = '<i class="bi bi-trash"></i> Delete';
        deleteButton.title = 'Delete category';
        deleteButton.addEventListener('click', () => deleteCategory(category.id));

        cell.append(editButton, deleteButton);
        return cell;
    }

    function renderCategories(categories) {
        tableBody.replaceChildren();
        categoryCount.textContent = categories.length;
        emptyMessage.classList.toggle('d-none', categories.length !== 0);

        categories.forEach((category) => {
            const row = document.createElement('tr');
            appendCell(row, category.id);
            row.appendChild(createImageCell(category.image));
            appendCell(row, category.name);
            appendCell(row, category.description);
            row.appendChild(createActionsCell(category));
            tableBody.appendChild(row);
        });
    }

    async function loadCategories() {
        try {
            const response = await fetch(apiUrl);
            if (!response.ok) {
                throw new Error(await readError(response));
            }
            renderCategories(await response.json());
        } catch (error) {
            renderCategories([]);
            showMessage(error.message || 'Network error while loading categories.');
        }
    }

    function resetForm() {
        form.reset();
        idInput.value = '';
        imageHint.textContent = '';
        imageHint.classList.add('d-none');
        imagePreview.removeAttribute('src');
        imagePreview.classList.add('d-none');
    }

    document.querySelector('#addCategoryButton').addEventListener('click', () => {
        clearMessage();
        resetForm();
        modalTitle.textContent = 'Add Category';
        submitButton.textContent = 'Add Category';
        categoryModal.show();
    });

    async function openEditModal(id) {
        clearMessage();
        try {
            const response = await fetch(`${apiUrl}/${id}`);
            if (!response.ok) {
                throw new Error(await readError(response));
            }
            const category = await response.json();
            resetForm();
            idInput.value = category.id;
            nameInput.value = category.name || '';
            descriptionInput.value = category.description || '';
            if (category.image) {
                imageHint.textContent = `Current image: ${category.image}. Leave image empty to keep it.`;
                imageHint.classList.remove('d-none');
                imagePreview.src = `/uploads/${encodeURIComponent(category.image)}`;
                imagePreview.classList.remove('d-none');
            }
            modalTitle.textContent = 'Edit Category';
            submitButton.textContent = 'Save Changes';
            categoryModal.show();
        } catch (error) {
            showMessage(error.message || 'Network error while loading the category.');
        }
    }

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        clearMessage();
        submitButton.disabled = true;

        const id = idInput.value;
        try {
            const response = await fetch(id ? `${apiUrl}/${id}` : apiUrl, {
                method: id ? 'PUT' : 'POST',
                body: new FormData(form)
            });
            if (!response.ok) {
                throw new Error(await readError(response));
            }
            categoryModal.hide();
            await loadCategories();
            showMessage(id ? 'Category updated successfully.' : 'Category added successfully.', 'success');
        } catch (error) {
            showMessage(error.message || 'Network error while saving the category.');
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

    async function deleteCategory(id) {
        if (!window.confirm('Are you sure you want to delete this category?')) {
            return;
        }

        clearMessage();
        try {
            const response = await fetch(`${apiUrl}/${id}`, { method: 'DELETE' });
            if (!response.ok) {
                throw new Error(await readError(response));
            }
            await loadCategories();
            showMessage('Category deleted successfully.', 'success');
        } catch (error) {
            showMessage(error.message || 'Network error while deleting the category.');
        }
    }

    loadCategories();
})();
