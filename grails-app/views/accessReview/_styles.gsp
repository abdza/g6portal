<%-- Shared look for the access review pages. Scoped to .ar-page so no other page is touched. --%>
<style>
.ar-page { --ar-line: #e4e6eb; --ar-muted: #6b7280; }
.ar-page h1 { font-size: 1.5rem; font-weight: 600; margin: 0; }
.ar-page h3 { font-size: 1.05rem; font-weight: 600; margin: 0 0 .6rem; }
.ar-page h4 { font-size: .95rem; font-weight: 600; margin: 1rem 0 .5rem; }
.ar-muted, .ar-page .ar-sub { color: var(--ar-muted); font-weight: 400; font-size: .85rem; }
.ar-head { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 1rem; align-items: flex-end;
           border-bottom: 1px solid var(--ar-line); padding-bottom: 1rem; margin-bottom: 1rem; }
.ar-due { text-align: right; font-size: .85rem; color: var(--ar-muted); }
.ar-due-n { font-size: 2rem; font-weight: 600; color: #222; line-height: 1; }
.ar-due-n.ar-urgent { color: #c0392b; }
.ar-badge { font-size: .7rem; font-weight: 600; padding: .2rem .5rem; border-radius: 999px; vertical-align: middle; }
.ar-pending { background: #fff4d6; color: #8a6100; }
.ar-approved { background: #dcf5e8; color: #146c43; }
.ar-revoked { background: #fde2df; color: #a4281c; }
.ar-cancelled { background: #eceef1; color: #555; }
.ar-steps { background: #eef4fc; color: #1e4f8a; border-radius: 8px; padding: .7rem 1rem; font-size: .9rem; margin-bottom: 1rem; }
.ar-card { background: #fff; border: 1px solid var(--ar-line); border-radius: 12px; padding: 1rem 1.25rem; margin-bottom: 1.25rem; }
.ar-card-quiet { background: #fafbfc; }
.ar-approve { border-color: #9fd8b8; }
.ar-page table.ar-table { font-size: .875rem; margin: 0; border: 0; }
.ar-page table.ar-table th { background: #f7f8fa; background-image: none; color: var(--ar-muted); font-size: .75rem; font-weight: 600;
                             text-transform: uppercase; white-space: normal; border: 0; border-bottom: 1px solid var(--ar-line); }
.ar-page table.ar-table td { border: 0; border-bottom: 1px solid #f0f1f3; vertical-align: middle; }
.ar-inactive td { color: #999; }
.ar-flag { font-size: .7rem; background: #fde2df; color: #a4281c; border-radius: 4px; padding: 0 .3rem; }
.ar-add { display: flex; flex-wrap: wrap; gap: .5rem; align-items: center; }
.ar-add-user { flex: 1 1 280px; }
.ar-add select.form-select { width: auto; min-width: 180px; }
.ar-confirm { display: flex; gap: .5rem; align-items: flex-start; margin-bottom: .6rem; font-weight: 500; }
.ar-pre { white-space: pre-line; margin: 0; }
.ar-stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: .75rem; margin-bottom: 1.25rem; }
.ar-stat { border: 1px solid var(--ar-line); border-radius: 10px; padding: .75rem 1rem; background: #fff; }
.ar-stat .v { font-size: 1.6rem; font-weight: 600; line-height: 1.1; }
.ar-stat .l { font-size: .8rem; color: var(--ar-muted); }
</style>
