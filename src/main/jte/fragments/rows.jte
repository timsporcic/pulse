@import org.sporcic.pulse.domain.MonitorView

@param java.util.List<MonitorView> monitors

@if(monitors.isEmpty())
  <div class="px-5 py-10 text-center text-ink/50">
    Add your first monitor to start watching a site.
  </div>
@endif
@for(MonitorView m : monitors)
  !{var edge = m.pending() ? "border-l-ink/20" : (m.up() ? "border-l-up" : "border-l-down");}
  !{var dot = m.pending() ? "bg-ink/20" : (m.up() ? "bg-up" : "bg-down");}
  <article class="flex items-center gap-4 border-l-[3px] ${edge} px-5 py-4">
    <span class="h-2.5 w-2.5 shrink-0 rounded-full ${dot}"></span>
    <div class="min-w-0 flex-1">
      <h2 class="truncate font-medium">${m.name()}</h2>
      <p class="truncate text-sm text-ink/50">${m.url()}</p>
    </div>
    <div class="text-right">
      @if(m.pending())
        <p class="text-sm text-ink/40">waiting for first check</p>
      @elseif(m.up())
        <p class="text-lg font-light tabular-nums">${m.latencyMs()}<span class="text-sm text-ink/50"> ms</span></p>
        <p class="text-sm tabular-nums text-ink/50">${String.format("%.1f%% uptime", m.uptimePct())}</p>
      @else
        <p class="text-lg font-light text-down">down</p>
        <p class="text-sm tabular-nums text-ink/50">${String.format("%.1f%% uptime", m.uptimePct())}</p>
      @endif
    </div>
    <button hx-delete="/monitors/${m.id()}" hx-swap="none" aria-label="Delete ${m.name()}"
            class="ml-2 rounded-md px-2 py-1 text-sm text-ink/40 hover:bg-down/10 hover:text-down">
      Remove
    </button>
  </article>
@endfor
