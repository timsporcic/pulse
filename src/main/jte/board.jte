@param java.util.List<org.sporcic.pulse.domain.MonitorView> monitors

<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Pulse</title>
  <link rel="stylesheet" href="/tailwind.css">
  <script src="/htmx.min.js" defer></script>
  <script src="/monitor-form.js" defer></script>
</head>
<body class="min-h-screen bg-mist text-ink antialiased">

  <header class="border-b border-ink/10 bg-white">
    <div class="mx-auto flex max-w-4xl items-center gap-3 px-6 py-4">
      <svg class="h-6 w-10 text-pine" viewBox="0 0 40 24" fill="none" aria-hidden="true">
        <path d="M0 12h9l3-8 5 16 4-12 2 4h17" stroke="currentColor" stroke-width="2"
              stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
      <span class="text-xl font-semibold tracking-tight">Pulse</span>
    </div>
  </header>

  <main class="mx-auto max-w-4xl px-6 py-10">

    <h1 class="mb-6 text-2xl font-semibold tracking-tight">Monitors</h1>

    <form id="monitor-form" hx-post="/monitors" hx-swap="none"
          hx-on::after:request="if (event.detail.ctx.response.status === 201) { this.reset(); this.elements.namedItem('name').focus(); }"
          class="mb-6 flex flex-wrap items-end gap-3 rounded-lg border border-ink/10 bg-white p-4">
      <label class="flex-1 min-w-40">
        <span class="mb-1 block text-sm text-ink/60">Name</span>
        <input name="name" required placeholder="My site"
               class="w-full rounded-md border border-ink/20 px-3 py-2 text-sm focus:border-pine focus:outline-none">
      </label>
      <label class="flex-[2] min-w-64">
        <span class="mb-1 block text-sm text-ink/60">URL</span>
        <input name="url" type="url" required placeholder="https://example.org"
               class="w-full rounded-md border border-ink/20 px-3 py-2 text-sm focus:border-pine focus:outline-none">
      </label>
      <button type="submit"
              class="rounded-md bg-pine px-3.5 py-2 text-sm font-medium text-white hover:bg-pine-deep">
        Add monitor
      </button>
    </form>

    <section id="board" hx-get="/board" hx-trigger="every 5s, monitorsChanged from:body" hx-swap="innerHTML"
             class="divide-y divide-ink/10 overflow-hidden rounded-lg border border-ink/10 bg-white">
      @template.fragments.rows(monitors)
    </section>

    <p class="mt-4 text-sm text-ink/40">The board refreshes every 5 seconds.</p>

  </main>

</body>
</html>
