(() => {
    const state = { page: 0, size: 5, keyword: '', timer: null };
    const tableBody = document.querySelector('#productTableBody');
    const empty = document.querySelector('#emptyProductMessage');
    const count = document.querySelector('#productCount');
    const pagination = document.querySelector('#productPagination');
    const loading = document.querySelector('#productLoading');
    const alertBox = document.querySelector('#productAlert');
    const search = document.querySelector('#productSearch');
    const form = document.querySelector('#productForm');
    const modal = new bootstrap.Modal(document.querySelector('#productModal'));
    const modalTitle = document.querySelector('#productModalLabel');
    const submit = document.querySelector('#productSubmitButton');
    const idInput = document.querySelector('#productId');
    const nameInput = document.querySelector('#productName');
    const quantityInput = document.querySelector('#productQuantity');
    const priceInput = document.querySelector('#productPrice');
    const categoryInput = document.querySelector('#productCategory');
    const imageInput = document.querySelector('#productImage');
    const fields = 'id name quantity price image category { id name } owner { id username fullname }';

    function showMessage(text, type = 'danger') { alertBox.textContent = text; alertBox.className = `alert alert-${type}`; }
    function clearMessage() { alertBox.textContent = ''; alertBox.className = 'alert d-none'; }
    function setLoading(isLoading) { loading.classList.toggle('d-none', !isLoading); }
    function textCell(row, text, className = '') { const cell = document.createElement('td'); cell.textContent = text ?? ''; cell.className = className; row.appendChild(cell); }
    function imageCell(imageName) { const cell = document.createElement('td'); if (imageName) { const image = document.createElement('img'); image.src = graphqlImageUrl(imageName); image.alt = 'Product image'; image.width = 50; image.height = 50; cell.appendChild(image); } return cell; }
    function actionCell(product) {
        const cell = document.createElement('td'); cell.className = 'text-end';
        [['Edit', 'btn-outline-primary me-2', () => openEdit(product.id)], ['Delete', 'btn-outline-danger', () => remove(product.id)]].forEach(([label, classes, handler]) => {
            const button = document.createElement('button'); button.type = 'button'; button.className = `btn btn-sm ${classes}`; button.textContent = label; button.addEventListener('click', handler); cell.appendChild(button);
        });
        return cell;
    }
    function render(page) {
        tableBody.replaceChildren(); count.textContent = page.totalElements; empty.classList.toggle('d-none', page.content.length !== 0);
        page.content.forEach(product => { const row = document.createElement('tr'); textCell(row, product.id); row.appendChild(imageCell(product.image)); textCell(row, product.name); textCell(row, product.quantity); textCell(row, new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(product.price), 'fw-semibold'); textCell(row, product.category.name); textCell(row, product.owner ? `${product.owner.fullname} (${product.owner.username})` : 'Legacy / unassigned'); row.appendChild(actionCell(product)); tableBody.appendChild(row); });
        renderPagination(page);
    }
    function renderPagination(page) {
        pagination.replaceChildren(); if (page.totalPages <= 1) return;
        const add = (label, target, disabled, active = false) => { const item = document.createElement('li'); item.className = `page-item${disabled ? ' disabled' : ''}${active ? ' active' : ''}`; const button = document.createElement('button'); button.type = 'button'; button.className = 'page-link'; button.textContent = label; button.disabled = disabled; button.addEventListener('click', () => { state.page = target; load(); }); item.appendChild(button); pagination.appendChild(item); };
        add('Previous', page.page - 1, page.page === 0);
        for (let index = 0; index < page.totalPages; index += 1) add(String(index + 1), index, false, index === page.page);
        add('Next', page.page + 1, page.page >= page.totalPages - 1);
    }
    async function load() {
        clearMessage(); setLoading(true);
        try { const data = await graphqlRequest(`query($keyword: String, $page: Int, $size: Int) { searchProducts(keyword: $keyword, page: $page, size: $size) { content { ${fields} } page size totalElements totalPages } }`, { keyword: state.keyword || null, page: state.page, size: state.size }); render(data.searchProducts); }
        catch (error) { render({ content: [], totalElements: 0, totalPages: 0 }); showMessage(error.message); }
        finally { setLoading(false); }
    }
    async function loadCategories(selected = '') {
        const data = await graphqlRequest('query { categories { id name } }'); categoryInput.replaceChildren();
        const placeholder = document.createElement('option'); placeholder.value = ''; placeholder.textContent = 'Select a category'; placeholder.disabled = true; placeholder.selected = !selected; categoryInput.appendChild(placeholder);
        data.categories.forEach(category => { const option = document.createElement('option'); option.value = category.id; option.textContent = category.name; option.selected = String(category.id) === String(selected); categoryInput.appendChild(option); });
    }
    function resetForm() { form.reset(); idInput.value = ''; }
    document.querySelector('#addProductButton').addEventListener('click', async () => { clearMessage(); resetForm(); try { await loadCategories(); modalTitle.textContent = 'Add Product'; submit.textContent = 'Add Product'; modal.show(); } catch (error) { showMessage(error.message); } });
    async function openEdit(id) {
        clearMessage();
        try { const data = await graphqlRequest(`query($id: ID!) { productById(id: $id) { ${fields} } }`, { id }); const product = data.productById; if (!product) throw new Error('Product not found'); resetForm(); await loadCategories(product.category.id); idInput.value = product.id; nameInput.value = product.name; quantityInput.value = product.quantity; priceInput.value = product.price; imageInput.value = product.image || ''; modalTitle.textContent = 'Edit Product'; submit.textContent = 'Save Changes'; modal.show(); } catch (error) { showMessage(error.message); }
    }
    form.addEventListener('submit', async event => {
        event.preventDefault(); clearMessage(); submit.disabled = true; const id = idInput.value;
        const input = { name: nameInput.value, quantity: Number(quantityInput.value), price: Number(priceInput.value), image: imageInput.value.trim() || null, categoryId: Number(categoryInput.value) };
        try { if (id) await graphqlRequest(`mutation($id: ID!, $input: ProductInput!) { updateProduct(id: $id, input: $input) { id } }`, { id: Number(id), input }); else await graphqlRequest('mutation($input: ProductInput!) { createProduct(input: $input) { id } }', { input }); modal.hide(); state.page = 0; await load(); showMessage(id ? 'Product updated successfully.' : 'Product added successfully.', 'success'); } catch (error) { showMessage(error.message); } finally { submit.disabled = false; }
    });
    async function remove(id) {
        if (!window.confirm('Delete this product?')) return; clearMessage();
        try { await graphqlRequest('mutation($id: ID!) { deleteProduct(id: $id) }', { id }); await load(); showMessage('Product deleted successfully.', 'success'); } catch (error) { showMessage(error.message); }
    }
    search.addEventListener('input', () => { window.clearTimeout(state.timer); state.timer = window.setTimeout(() => { state.keyword = search.value.trim(); state.page = 0; load(); }, 300); });
    load();
})();
