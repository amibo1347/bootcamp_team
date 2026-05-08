(() => {
  function formatDate(value) {
    if (!value) return '-';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }

  function escapeHtml(value) {
    return String(value || '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#39;');
  }

  async function loadPosts(boardId) {
    if (!boardId) return [];
    const res = await fetch(`/board/${boardId}/articles`, {
      headers: { 'Accept': 'application/json' },
      credentials: 'same-origin',
    });
    if (!res.ok) return [];
    return res.json();
  }

  document.addEventListener('DOMContentLoaded', async () => {
    const boardId = Number(document.body.dataset.boardId || 0);
    if (!boardId) return;
    const posts = await loadPosts(boardId);
    renderList(posts);
    renderAlbum(posts);
    renderCard(posts);
  });

  function renderList(posts) {
    const body = document.getElementById('postListBody');
    const empty = document.getElementById('postListEmpty');
    if (!body || !empty) return;
    if (!posts.length) {
      empty.classList.remove('hidden');
      body.innerHTML = '';
      return;
    }
    empty.classList.add('hidden');
    body.innerHTML = posts
      .map(
        (post, index) => `
          <tr class="border-t border-gray-100 text-gray-700 dark:border-strokedark dark:text-gray-200">
            <td class="px-5 py-3">${index + 1}</td>
            <td class="px-5 py-3"><a href="#" class="hover:text-indigo-500">${escapeHtml(post.title)}</a></td>
            <td class="px-5 py-3">${escapeHtml(post.authorName || '-')}</td>
            <td class="px-5 py-3">${formatDate(post.createdAt)}</td>
            <td class="px-5 py-3">${Number(post.viewCount || 0)}</td>
          </tr>
        `
      )
      .join('');
  }

  function renderAlbum(posts) {
    const grid = document.getElementById('postAlbumGrid');
    const empty = document.getElementById('postAlbumEmpty');
    if (!grid || !empty) return;
    if (!posts.length) {
      empty.classList.remove('hidden');
      grid.innerHTML = '';
      return;
    }
    empty.classList.add('hidden');
    grid.innerHTML = posts
      .map(
        (post) => `
          <article class="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm dark:border-strokedark dark:bg-boxdark">
            ${
              post.thumbnailUrl
                ? `<img src="${escapeHtml(post.thumbnailUrl)}" alt="thumbnail" class="h-48 w-full object-cover" />`
                : `<div class="flex h-48 items-center justify-center bg-gray-100 text-sm text-gray-400 dark:bg-meta-4/60">이미지 없음</div>`
            }
            <div class="space-y-2 p-4">
              <h3 class="line-clamp-1 text-base font-semibold text-gray-900 dark:text-white">${escapeHtml(post.title)}</h3>
              <p class="line-clamp-2 text-sm text-gray-600 dark:text-gray-300">${escapeHtml(post.summary || '')}</p>
              <div class="flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
                <span>${escapeHtml(post.authorName || '-')}</span>
                <span>${formatDate(post.createdAt)}</span>
              </div>
            </div>
          </article>
        `
      )
      .join('');
  }

  function renderCard(posts) {
    const grid = document.getElementById('postCardGrid');
    const empty = document.getElementById('postCardEmpty');
    if (!grid || !empty) return;
    if (!posts.length) {
      empty.classList.remove('hidden');
      grid.innerHTML = '';
      return;
    }
    empty.classList.add('hidden');
    grid.innerHTML = posts
      .map(
        (post) => `
          <article class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-strokedark dark:bg-boxdark">
            <div class="mb-3 flex items-start justify-between gap-3">
              <h3 class="line-clamp-2 text-lg font-semibold text-gray-900 dark:text-white">${escapeHtml(post.title)}</h3>
              <span class="shrink-0 rounded-full bg-indigo-100 px-2.5 py-1 text-xs font-medium text-indigo-700">${Number(
                post.viewCount || 0
              )} views</span>
            </div>
            <p class="line-clamp-3 text-sm leading-6 text-gray-600 dark:text-gray-300">${escapeHtml(post.content || '')}</p>
            <div class="mt-4 flex flex-wrap items-center gap-2">
              ${(post.tags || [])
                .map(
                  (tag) =>
                    `<span class="rounded-full bg-gray-100 px-2.5 py-0.5 text-xs text-gray-600 dark:bg-meta-4/60 dark:text-gray-300">#${escapeHtml(
                      tag
                    )}</span>`
                )
                .join('')}
            </div>
            <div class="mt-5 flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
              <span>${escapeHtml(post.authorName || '-')}</span>
              <span>${formatDate(post.createdAt)}</span>
            </div>
          </article>
        `
      )
      .join('');
  }

  document.addEventListener('DOMContentLoaded', () => {
    const boardId = Number(document.body.dataset.boardId || 0);
    if (!boardId) return;
    const posts = loadPosts(boardId);
    renderList(posts);
    renderAlbum(posts);
    renderCard(posts);
  });
})();
