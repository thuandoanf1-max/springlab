(() => {
    const categorySelect = document.querySelector('#homeCategory');
    const grid = document.querySelector('#homeProductGrid');
    const loading = document.querySelector('#homeLoading');
    const empty = document.querySelector('#homeEmpty');
    const alertBox = document.querySelector('#homeAlert');
    const status = document.querySelector('#homeStatus');
    const productFields = 'id name price quantity image category { id name }';

    function message(text) { alertBox.textContent = text; alertBox.className = 'alert alert-danger'; }
    function clearMessage() { alertBox.textContent = ''; alertBox.className = 'alert d-none'; }
    function setLoading(isLoading) { loading.classList.toggle('d-none', !isLoading); }
    function render(products) {
        grid.replaceChildren();
        empty.classList.toggle('d-none', products.length !== 0);
        products.forEach(product => {
            const column = document.createElement('div'); column.className = 'col-sm-6 col-lg-4';
            const card = document.createElement('article'); card.className = 'border rounded-3 h-100 p-3 bg-white';
            if (product.image) { const image = document.createElement('img'); image.src = graphqlImageUrl(product.image); image.alt = product.name; image.className = 'w-100 mb-3'; image.style.height = '150px'; image.style.objectFit = 'cover'; card.appendChild(image); }
            const title = document.createElement('h3'); title.className = 'h6 mb-1'; title.textContent = product.name;
            const category = document.createElement('p'); category.className = 'small text-muted mb-2'; category.textContent = product.category.name;
            const price = document.createElement('strong'); price.className = 'text-primary'; price.textContent = new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(product.price);
            const quantity = document.createElement('span'); quantity.className = 'small text-muted ms-2'; quantity.textContent = `Qty: ${product.quantity}`;
            card.append(title, category, price, quantity); column.appendChild(card); grid.appendChild(column);
        });
    }
    async function loadCategories() {
        const data = await graphqlRequest('query { categories { id name } }');
        data.categories.forEach(category => { const option = document.createElement('option'); option.value = category.id; option.textContent = category.name; categorySelect.appendChild(option); });
    }
    async function loadProducts() {
        clearMessage(); setLoading(true);
        try {
            const id = categorySelect.value;
            const data = id
                ? await graphqlRequest(`query($categoryId: ID!) { productsByCategory(categoryId: $categoryId) { ${productFields} } }`, { categoryId: Number(id) })
                : await graphqlRequest(`query { productsByPriceAsc { ${productFields} } }`);
            const products = id ? data.productsByCategory : data.productsByPriceAsc;
            status.textContent = id ? `${products.length} products in this category` : `${products.length} products, price ascending`;
            render(products);
        } catch (error) { render([]); message(error.message); }
        finally { setLoading(false); }
    }
    categorySelect.addEventListener('change', loadProducts);
    (async () => { try { await loadCategories(); } catch (error) { message(error.message); } await loadProducts(); })();
})();
