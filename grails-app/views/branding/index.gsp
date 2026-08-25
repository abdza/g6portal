<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="main"/>
    <title>Branding</title>
    <style>
      .brand-swatch { display:flex; align-items:center; gap:10px; }
      .brand-swatch input[type=color] { width:46px; height:38px; padding:2px; border:1px solid #ced4da; border-radius:4px; background:#fff; }
      .brand-swatch input[type=text] { max-width:130px; font-family:monospace; }
      .brand-hint { font-size:12px; color:#899bbd; margin-bottom:0; }
      .brand-preview { border:1px solid #ebeef4; border-radius:6px; overflow:hidden; }
      .brand-preview .pv-header { height:46px; display:flex; align-items:center; padding:0 14px; font-weight:700; box-shadow:0 2px 10px rgba(1,41,112,.08); }
      .brand-preview .pv-body { display:flex; min-height:150px; }
      .brand-preview .pv-side { width:38%; padding:12px; }
      .brand-preview .pv-main { flex:1; padding:14px; }
      .brand-preview .pv-nav { display:block; padding:8px 10px; border-radius:4px; font-size:13px; font-weight:600; margin-bottom:6px; }
      .brand-preview .pv-btn { display:inline-block; padding:6px 14px; border-radius:4px; color:#fff; font-size:13px; }
      .brand-preview .pv-logo { max-height:22px; margin-right:8px; }
    </style>
</head>

<body>
<div class="pagetitle">
  <h1>Branding</h1>
  <nav>
    <ol class="breadcrumb">
      <li class="breadcrumb-item"><g:link controller="portalPage" action="home">Home</g:link></li>
      <li class="breadcrumb-item active">Branding</li>
    </ol>
  </nav>
</div>

<g:if test="${flash.message}">
  <div class="alert alert-info alert-dismissible fade show" role="alert">
    ${flash.message}
    <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
  </div>
</g:if>

<div class="row">

  <div class="col-lg-7">
    <g:form controller="branding" action="save" method="post" useToken="true" enctype="multipart/form-data">
    <div class="card">
      <div class="card-body">
        <h5 class="card-title">Identity</h5>

        <div class="row mb-3">
          <label class="col-sm-4 col-form-label" for="app_name">Portal name</label>
          <div class="col-sm-8">
            <input type="text" class="form-control" id="app_name" name="app_name" value="${branding.app_name}"/>
            <p class="brand-hint">Shown in the browser tab and beside the logo.</p>
          </div>
        </div>

        <div class="row mb-3">
          <label class="col-sm-4 col-form-label" for="logo">Logo</label>
          <div class="col-sm-8">
            <g:if test="${branding.logo_slug}">
              <p class="mb-2"><img src="<g:brand_logo_url/>" alt="Current logo" style="max-height:30px"/></p>
              <div class="form-check mb-2">
                <input class="form-check-input" type="checkbox" name="remove_logo" id="remove_logo" value="1"/>
                <label class="form-check-label" for="remove_logo">Remove current logo</label>
              </div>
            </g:if>
            <input type="file" class="form-control" id="logo" name="logo" accept="image/png,image/jpeg,image/gif"/>
            <p class="brand-hint">PNG, JPG or GIF, up to 2&nbsp;MB. Displays at 26&nbsp;px tall, so a wide logo works best.</p>
          </div>
        </div>

        <div class="row mb-3">
          <label class="col-sm-4 col-form-label" for="logo_url">Logo URL</label>
          <div class="col-sm-8">
            <input type="text" class="form-control" id="logo_url" name="logo_url" value="${branding.logo_url}"/>
            <p class="brand-hint">Only used when no logo file is uploaded above.</p>
          </div>
        </div>

        <div class="row mb-3">
          <label class="col-sm-4 col-form-label" for="favicon">Favicon</label>
          <div class="col-sm-8">
            <g:if test="${branding.favicon_slug}">
              <p class="mb-2"><img src="<g:brand_favicon_url/>" alt="Current favicon" style="max-height:24px"/></p>
              <div class="form-check mb-2">
                <input class="form-check-input" type="checkbox" name="remove_favicon" id="remove_favicon" value="1"/>
                <label class="form-check-label" for="remove_favicon">Remove current favicon</label>
              </div>
            </g:if>
            <input type="file" class="form-control" id="favicon" name="favicon" accept="image/png,image/jpeg,image/gif"/>
            <p class="brand-hint">A square PNG of 32&times;32 or 96&times;96 works best.</p>
          </div>
        </div>

        <h5 class="card-title">Footer</h5>

        <div class="row mb-3">
          <label class="col-sm-4 col-form-label" for="copyright">Copyright holder</label>
          <div class="col-sm-8">
            <input type="text" class="form-control" id="copyright" name="copyright" value="${branding.copyright}"/>
          </div>
        </div>

        <div class="row mb-3">
          <label class="col-sm-4 col-form-label" for="team">Credit line</label>
          <div class="col-sm-8">
            <input type="text" class="form-control" id="team" name="team" value="${branding.team}"/>
          </div>
        </div>

        <div class="row mb-3">
          <label class="col-sm-4 col-form-label" for="homepage">Credit link</label>
          <div class="col-sm-8">
            <input type="text" class="form-control" id="homepage" name="homepage" value="${branding.homepage}"/>
          </div>
        </div>

        <h5 class="card-title">Colours</h5>
        <g:each in="${colorKeys}" var="key">
        <div class="row mb-3">
          <label class="col-sm-4 col-form-label" for="color_${key}">
            <g:if test="${key=='primary'}">Primary</g:if>
            <g:elseif test="${key=='accent'}">Accent</g:elseif>
            <g:elseif test="${key=='heading'}">Heading text</g:elseif>
            <g:elseif test="${key=='page_bg'}">Page background</g:elseif>
            <g:elseif test="${key=='header_bg'}">Top bar</g:elseif>
            <g:elseif test="${key=='sidebar_bg'}">Side menu</g:elseif>
            <g:else>${key}</g:else>
          </label>
          <div class="col-sm-8">
            <div class="brand-swatch">
              <input type="color" id="color_${key}" value="${branding[key]}" data-brand-key="${key}"/>
              <input type="text" class="form-control" name="color_${key}" value="${branding[key]}"
                     pattern="#[0-9a-fA-F]{6}" data-brand-text="${key}"/>
            </div>
            <p class="brand-hint">
              <g:if test="${key=='primary'}">Links, buttons, the selected menu item.</g:if>
              <g:elseif test="${key=='accent'}">The colour a link turns on hover.</g:elseif>
              <g:elseif test="${key=='heading'}">Page titles and the name beside the logo.</g:elseif>
              <g:elseif test="${key=='page_bg'}">Behind the content area.</g:elseif>
              <g:elseif test="${key=='header_bg'}">The bar across the top.</g:elseif>
              <g:elseif test="${key=='sidebar_bg'}">The menu down the left.</g:elseif>
              Default ${colorDefaults[key]}.
            </p>
          </div>
        </div>
        </g:each>

        <button type="submit" class="btn btn-primary">Save branding</button>
      </div>
    </div>
    </g:form>
  </div>

  <div class="col-lg-5">
    <div class="card">
      <div class="card-body">
        <h5 class="card-title">Preview</h5>
        <div class="brand-preview" id="brandPreview">
          <div class="pv-header">
            <img src="<g:brand_logo_url/>" class="pv-logo" alt=""/>
            <span data-pv="name">${branding.app_name}</span>
          </div>
          <div class="pv-body">
            <div class="pv-side">
              <span class="pv-nav" data-pv="nav-active">Dashboard</span>
              <span class="pv-nav" data-pv="nav">Reports</span>
              <span class="pv-nav" data-pv="nav">Settings</span>
            </div>
            <div class="pv-main">
              <h6 data-pv="heading">Page title</h6>
              <p style="font-size:13px">Body text with a <a href="#" data-pv="link" onclick="return false">link</a> in it.</p>
              <span class="pv-btn" data-pv="btn">Button</span>
            </div>
          </div>
        </div>
        <p class="brand-hint mt-2">Updates as you pick colours. Save to apply across the portal.</p>
      </div>
    </div>

    <%-- Its own form: nesting one form inside another is not valid HTML, so this sits
         alongside the save form rather than within it. --%>
    <div class="card">
      <div class="card-body">
        <h5 class="card-title">Start over</h5>
        <p class="brand-hint mb-3">Clears every branding value and returns the portal to its shipped look.</p>
        <g:form controller="branding" action="reset" method="post" useToken="true">
          <button type="submit" class="btn btn-outline-secondary btn-sm">Reset to defaults</button>
        </g:form>
      </div>
    </div>
  </div>

</div>

<asset:script type="text/javascript">
(function () {
  var preview = document.getElementById('brandPreview');
  if (!preview) return;

  function paint() {
    function val(k) {
      var el = document.querySelector('[data-brand-text="' + k + '"]');
      return el ? el.value : '';
    }
    var primary = val('primary'), accent = val('accent'), heading = val('heading');
    var pageBg = val('page_bg'), headerBg = val('header_bg'), sidebarBg = val('sidebar_bg');

    preview.querySelector('.pv-header').style.background = headerBg;
    preview.querySelector('.pv-header').style.color = heading;
    preview.querySelector('.pv-side').style.background = sidebarBg;
    preview.querySelector('.pv-main').style.background = pageBg;
    preview.querySelector('[data-pv="heading"]').style.color = heading;
    preview.querySelector('[data-pv="link"]').style.color = primary;
    preview.querySelector('[data-pv="btn"]').style.background = primary;

    var active = preview.querySelector('[data-pv="nav-active"]');
    active.style.background = pageBg;
    active.style.color = primary;
    Array.prototype.forEach.call(preview.querySelectorAll('[data-pv="nav"]'), function (el) {
      el.style.background = sidebarBg;
      el.style.color = heading;
    });
  }

  // The colour well and the hex box edit the same value, so each mirrors the other. Only
  // the text input carries a name attribute, so that is what actually gets submitted.
  Array.prototype.forEach.call(document.querySelectorAll('[data-brand-key]'), function (well) {
    var key = well.getAttribute('data-brand-key');
    var text = document.querySelector('[data-brand-text="' + key + '"]');
    well.addEventListener('input', function () { text.value = well.value; paint(); });
    text.addEventListener('input', function () {
      if (/^#[0-9a-fA-F]{6}$/.test(text.value)) { well.value = text.value; paint(); }
    });
  });

  var nameField = document.getElementById('app_name');
  if (nameField) {
    nameField.addEventListener('input', function () {
      preview.querySelector('[data-pv="name"]').textContent = nameField.value;
    });
  }

  paint();
})();
</asset:script>

</body>
</html>
