(() => {
    async function graphqlRequest(query, variables = {}) {
        const response = await fetch('/graphql', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ query, variables })
        });
        let result;
        try {
            result = await response.json();
        } catch (_) {
            throw new Error('The server returned an invalid response.');
        }
        if (!response.ok) {
            throw new Error(result?.message || 'The request could not be completed.');
        }
        if (result.errors?.length) {
            throw new Error(result.errors.map(error => error.message).join('\n'));
        }
        return result.data;
    }

    function imageUrl(image) {
        if (!image) return '';
        return image.startsWith('/') || /^https?:\/\//i.test(image)
            ? image
            : `/uploads/${encodeURIComponent(image)}`;
    }

    window.graphqlRequest = graphqlRequest;
    window.graphqlImageUrl = imageUrl;
})();
