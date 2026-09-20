(() => {
    const state = { page: 0, size: 5, keyword: '', timer: null };
    const tableBody = document.querySelector('#categoryTableBody');
    const empty = document.querySelector('#emptyMessage');
    const count = document.querySelector('#categoryCount');
    const pagination = document.querySelector('#categoryPagination');
    const loading = document.querySelector('#categoryLoading');
    const alertBox = document.querySelector('#categoryAlert');
    const search = document.querySelector('#categorySearch');
    const form = document.querySelector('#categoryForm');
    const modal = new bootstrap.Modal(document.querySelector('#categoryModal'));
    const modalTitle = document.querySelector('#categoryModalLabel');
    const submit = document.querySelector('#categorySubmitButton');
    const idInput = document.querySelector('#categoryId');
    const nameInput = document.querySelector('#categoryName');
    const descriptionInput = document.querySelector('#categoryDescription');
    const imageInput = document.querySelector('#categoryImage');
    const fields = 'id name description image';

    function showMessage(text, type = 'danger') { alertBox.textContent = text; alertBox.className = `alert alert-${type}`; }
    function clearMessage() { alertBox.textContent = ''; alertBox.className = 'alert d-none'; }
    function setLoading(isLoading) { loading.classList.toggle('d-none', !isLoading); }
    function cell(row, value) { const element = document.createElement('td'); element.textContent = value ?? ''; row.appendChild(element); }
    function imageCell(value) { const cell = document.createElement('td'); if (value) { const image = document.createElement('img'); image.src = graphqlImageUrl(value); image.alt = 'Category image'; image.width = 50; image.height = 50; cell.appendChild(image); } return cell; }
    function actions(category) { const cell = document.createElement('td'); cell.className = 'text-end'; [['Edit', 'btn-outline-primary me-2', () => openEdit(category.id)], ['Delete', 'btn-outline-danger', () => remove(category.id)]].forEach(([label, classes, handler]) => { const button = document.createElement('button'); button.type = 'button'; button.className = `btn btn-sm ${classes}`; button.textContent = label; button.addEventListener('click', handler); cell.appendChild(button); }); return cell; }
    function render(page) { tableBody.replaceChildren(); count.textContent = page.totalElements; empty.classList.toggle('d-none', page.content.length !== 0); page.content.forEach(category => { const row = document.createElement('tr'); cell(row, category.id); row.appendChild(imageCell(category.image)); cell(row, category.name); cell(row, category.description); row.appendChild(actions(category)); tableBody.appendChild(row); }); renderPagination(page); }
    function renderPagination(page) { pagination.replaceChildren(); if (page.totalPages <= 1) return; const add = (label, target, disabled, active = false) => { const item = document.createElement('li'); item.className = `page-item${disabled ? ' disabled' : ''}${active ? ' active' : ''}`; const button = document.createElement('button'); button.type = 'button'; button.className = 'page-link'; button.textContent = label; button.disabled = disabled; button.addEventListener('click', () => { state.page = target; load(); }); item.appendChild(button); pagination.appendChild(item); }; add('Previous', page.page - 1, page.page === 0); for (let index = 0; index < page.totalPages; index += 1) add(String(index + 1), index, false, index === page.page); add('Next', page.page + 1, page.page >= page.totalPages - 1); }
    async function load() { clearMessage(); setLoading(true); try { const data = await graphqlRequest(`query($keyword: String, $page: Int, $size: Int) { searchCategories(keyword: $keyword, page: $page, size: $size) { content { ${fields} } page size totalElements totalPages } }`, { keyword: state.keyword || null, page: state.page, size: state.size }); render(data.searchCategories); } catch (error) { render({ content: [], totalElements: 0, totalPages: 0 }); showMessage(error.message); } finally { setLoading(false); } }
    function resetForm() { form.reset(); idInput.value = ''; }
    document.querySelector('#addCategoryButton').addEventListener('click', () => { clearMessage(); resetForm(); modalTitle.textContent = 'Add Category'; submit.textContent = 'Add Category'; modal.show(); });
    async function openEdit(id) { clearMessage(); try { const data = await graphqlRequest(`query($id: ID!) { categoryById(id: $id) { ${fields} } }`, { id }); const category = data.categoryById; if (!category) throw new Error('Category not found'); resetForm(); idInput.value = category.id; nameInput.value = category.name; descriptionInput.value = category.description || ''; imageInput.value = category.image || ''; modalTitle.textContent = 'Edit Category'; submit.textContent = 'Save Changes'; modal.show(); } catch (error) { showMessage(error.message); } }
    form.addEventListener('submit', async event => { event.preventDefault(); clearMessage(); submit.disabled = true; const id = idInput.value; const input = { name: nameInput.value, description: descriptionInput.value.trim() || null, image: imageInput.value.trim() || null }; try { if (id) await graphqlRequest('mutation($id: ID!, $input: CategoryInput!) { updateCategory(id: $id, input: $input) { id } }', { id: Number(id), input }); else await graphqlRequest('mutation($input: CategoryInput!) { createCategory(input: $input) { id } }', { input }); modal.hide(); state.page = 0; await load(); showMessage(id ? 'Category updated successfully.' : 'Category added successfully.', 'success'); } catch (error) { showMessage(error.message); } finally { submit.disabled = false; } });
    async function remove(id) { if (!window.confirm('Delete this category?')) return; clearMessage(); try { await graphqlRequest('mutation($id: ID!) { deleteCategory(id: $id) }', { id }); await load(); showMessage('Category deleted successfully.', 'success'); } catch (error) { showMessage(error.message); } }
    search.addEventListener('input', () => { window.clearTimeout(state.timer); state.timer = window.setTimeout(() => { state.keyword = search.value.trim(); state.page = 0; load(); }, 300); });
    load();
})();
