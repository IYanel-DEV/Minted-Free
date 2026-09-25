/* Minted Global Shop Editor - app logic
   Single global shop. Categories = tabs. Items edited in the right panel.
*/

// ================= state =================
let shopName = localStorage.getItem("ms_shopName") || "Global Shop";
let catalog = localStorage.getItem("ms_catalog") !== "false";
let items = [];
try { items = JSON.parse(localStorage.getItem("ms_items") || "[]"); } catch (e) { items = []; }
let cats = [];
try { cats = JSON.parse(localStorage.getItem("ms_cats") || "[]"); } catch (e) { cats = []; }
if (!cats.length) cats = ["General"];

let curCat = cats[0];
let selectedIdx = -1;        // index into items
let editing = false;         // is an item selected?
let pickFilterCat = 0;
let pickQuery = "";

const num = (v, d) => (v === undefined || v === null || v === "" || isNaN(v)) ? d : v;
const clean = (o) => { Object.keys(o).forEach(k => { if (o[k] === undefined || o[k] === null || o[k] === "" || (Array.isArray(o[k]) && !o[k].length)) delete o[k]; }); return o; };

const save = () => {
    localStorage.setItem("ms_shopName", shopName);
    localStorage.setItem("ms_catalog", catalog ? "true" : "false");
    localStorage.setItem("ms_items", JSON.stringify(items));
    localStorage.setItem("ms_cats", JSON.stringify(cats));
};

// ================= layout =================
const DISPLAY = [10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43];
// rows: 1..4 centers. We'll fill content slots (non-border) sequentially with items.
const isBorder = (s) => { const r = Math.floor(s/9), c = s%9; return r===0 || r===5 || c===0 || c===8; };

// ================= icons =================
function texIt(it) {
    return {
        key: it[0],
        title: it[1],
        path: texPath(it),
        blockPath: blockTexPath(it),
    };
}
function desc(key) {
    const i = ITEM_BY_KEY[key];
    return i === undefined ? null : texIt(ITEMS[i]);
}
function iconEl(key, cls) {
    const d = desc(key);
    const img = document.createElement("img");
    img.className = "icon" + (cls ? " " + cls : "");
    img.loading = "lazy";
    img.alt = key || "?";
    if (d) {
        img.dataset.block = "0";
        img.onerror = function () {
            if (img.dataset.block === "0" && d.blockPath) { img.dataset.block = "1"; img.src = d.blockPath; }
            else {
                img.style.background = "#333";
                img.style.border = "1px solid #555";
                img.style.display = "flex";
                img.style.alignItems = "center";
                img.style.justifyContent = "center";
                img.style.fontSize = "7px";
                img.style.color = "#888";
                img.src = "";
                img.removeAttribute("src");
                img.textContent = "?";
            }
        };
        img.src = d.path;
    } else {
        img.style.background = "#333";
        img.style.border = "1px solid #555";
        img.style.display = "flex";
        img.style.alignItems = "center";
        img.style.justifyContent = "center";
        img.style.fontSize = "7px";
        img.style.color = "#888";
        img.textContent = "?";
    }
    return img;
}

function matTitle(m) {
    const d = desc(m);
    if (d && d.title) return d.title;
    return m.toLowerCase().replace(/_/g, " ").replace(/\b\w/g, c => c.toUpperCase());
}
const cleanName = (s) => String(s || "").replace(/&[0-9a-fk-or]/gi, "");

// ================= rendering =================
const gridEl = () => document.getElementById("grid");
const catEl = () => document.getElementById("catTabs");
const catsFor = () => {
    const set = [cats[0]];
    items.forEach(it => { if (it.category && set.indexOf(it.category) === -1) set.push(it.category); });
    return set;
};
const itemsInCat = () => items.filter(it => (it.category || cats[0]) === curCat);
const itemsOther = () => items.filter(it => (it.category || cats[0]) !== curCat);

function renderCats() {
    const el = catEl();
    el.innerHTML = "";
    const list = catsFor();
    if (list.indexOf(curCat) === -1) curCat = list[0] || "General";
    list.forEach(c => {
        const t = document.createElement("div");
        t.className = "tab" + (c === curCat ? " active" : "");
        t.textContent = c;
        t.title = c;
        t.addEventListener("click", () => { curCat = c; selectedIdx = -1; editing = false; renderAll(); });
        el.appendChild(t);
    });
}

function renderGrid() {
    const g = gridEl();
    g.innerHTML = "";
    const inCat = itemsInCat();
    // content slots: all non-border slots from 9..44 except we map items in order
    const contentSlots = [];
    for (let s = 9; s < 45; s++) if (!isBorder(s)) contentSlots.push(s);

    for (let s = 0; s < 54; s++) {
        const r = Math.floor(s/9), c = s%9;
        const div = document.createElement("div");
        div.className = "slot";
        // pixel-exact cells over the 176x132 chest region: slot box starts at (7,17), 18px pitch
        div.style.left = ((7 + c * 18) / 176 * 100) + "%";
        div.style.top = ((17 + r * 18) / 132 * 100) + "%";
        div.style.width = (18 / 176 * 100) + "%";
        div.style.height = (18 / 132 * 100) + "%";
        if (r === 0 || r === 5 || c === 0 || c === 8) {
            div.classList.add("border-slot");
            g.appendChild(div);
            continue;
        }
        const pos = contentSlots.indexOf(s);
        if (pos === -1) { div.classList.add("border-slot"); g.appendChild(div); continue; }

        if (pos < inCat.length) {
            const item = inCat[pos];
            // find real index
            const realIdx = items.indexOf(item);
            div.appendChild(iconEl(item.material, "32"));
            const amt = document.createElement("span");
            amt.className = "slot-amt";
            amt.textContent = "x" + amtNum(item);
            div.appendChild(amt);
            const tag = document.createElement("div");
            tag.className = "slot-tag";
            tag.textContent = cleanName(item.name) || matTitle(item.material);
            div.appendChild(tag);
            if (selectedIdx === realIdx) div.classList.add("selected");
            div.addEventListener("click", () => selectItem(realIdx));
        } else {
            div.classList.add("empty");
            const plus = document.createElement("div");
            plus.textContent = "+";
            plus.style.cssText = "font-size:20px;color:rgba(255,255,255,0.35);text-shadow:none;";
            div.appendChild(plus);
            div.addEventListener("click", () => addItem());
        }
        g.appendChild(div);
    }
}
const amtNum = (it) => num(it.amount, 1);

function renderAll() {
    renderCats();
    renderGrid();
    renderConfig();
    document.getElementById("pageLabel").textContent = items.length + " items";
    document.getElementById("shopName").value = shopName;
}

// ================= select / add =================
function selectItem(realIdx) {
    selectedIdx = realIdx;
    editing = true;
    renderAll();
    fillForm();
}
function addItem() {
    const it = { material: "STONE", amount: 1, page: 1, category: curCat };
    items.push(it);
    selectedIdx = items.length - 1;
    editing = true;
    save();
    renderAll();
    fillForm();
}

// ================= config panel =================
function fillForm() {
    if (!editing || selectedIdx < 0 || selectedIdx >= items.length) return;
    const it = items[selectedIdx];
    document.getElementById("fMaterial").value = it.material || "";
    document.getElementById("fAmount").value = amtNum(it);
    document.getElementById("fName").value = it.name || "";
    document.getElementById("fLore").value = (it.lore || []).join("\n");
    document.getElementById("fBuy").value = it.buy !== undefined ? it.buy : "";
    document.getElementById("fSell").value = it.sell !== undefined ? it.sell : "";
    document.getElementById("fPage").value = num(it.page, 1);
    document.getElementById("fSlot").value = it.slot !== undefined ? it.slot : "";
    document.getElementById("fCategory").value = it.category || cats[0];
    document.getElementById("fEnchants").value = it.enchants ? Object.entries(it.enchants).map(([k, v]) => k + ":" + v).join("\n") : "";
    document.getElementById("fData").value = it.data || 0;
    renderPreview();
}
function renderPreview() {
    const key = document.getElementById("fMaterial").value;
    const matView = document.getElementById("matPreview");
    matView.innerHTML = "";
    const cleanDesc = desc(key);
    if (cleanDesc) {
        matView.appendChild(iconEl(key, ""));
        const nm = document.createElement("span");
        nm.className = "mat-name";
        nm.textContent = cleanName(cleanDesc.title) + " (" + key + ")";
        matView.appendChild(nm);
    } else {
        const nm = document.createElement("span");
        nm.className = "mat-name";
        nm.textContent = "(unknown material)";
        matView.appendChild(nm);
    }
}
function renderConfig() {
    const form = document.getElementById("configForm");
    const hint = document.getElementById("emptyHint");
    const head = document.getElementById("configHead");
    const big = document.getElementById("bigIcon");
    const empty = document.getElementById("emptyText");
    if (editing && selectedIdx >= 0 && selectedIdx < items.length) {
        form.style.display = "block";
        hint.style.display = "none";
        head.textContent = "Item Settings";
        fillForm();
    } else {
        form.style.display = "none";
        hint.style.display = "block";
        head.textContent = curCat;
        big.innerHTML = "";
        const tree = desc(curCat) ? curCat : "CHEST";
        if (desc(tree)) big.appendChild(iconEl(tree, ""));
        empty.innerHTML = "Click an item slot to edit it.<br>Click empty slot to add an item.";
    }
}

function updateFromForm() {
    if (!editing || selectedIdx < 0 || selectedIdx >= items.length) return;
    const it = items[selectedIdx];
    it.amount = num(parseInt(document.getElementById("fAmount").value), 1);
    it.name = document.getElementById("fName").value.trim() || undefined;
    const lore = document.getElementById("fLore").value.split("\n").map(x => x.trim()).filter(Boolean);
    it.lore = lore.length ? lore : undefined;
    it.buy = document.getElementById("fBuy").value === "" ? undefined : parseFloat(document.getElementById("fBuy").value);
    it.sell = document.getElementById("fSell").value === "" ? undefined : parseFloat(document.getElementById("fSell").value);
    it.page = num(parseInt(document.getElementById("fPage").value), 1);
    it.slot = document.getElementById("fSlot").value === "" ? undefined : parseInt(document.getElementById("fSlot").value);
    it.category = document.getElementById("fCategory").value.trim() || cats[0];
    it.data = parseInt(document.getElementById("fData").value) || 0;
    const en = {};
    document.getElementById("fEnchants").value.split("\n").forEach(line => {
        const m = line.split(":");
        if (m.length >= 2 && m[0].trim()) en[m[0].trim().toLowerCase()] = parseInt(m[1].trim());
    });
    it.enchants = Object.keys(en).length ? en : undefined;
    clean(it);
    if (it.category && cats.indexOf(it.category) === -1) {
        cats.push(it.category);
        curCat = it.category;
    }
    save();
    renderAll();
    fillForm();
}

// form input listeners (live update)
["fAmount","fName","fLore","fBuy","fSell","fPage","fSlot","fCategory","fData"].forEach(id => {
    document.getElementById(id).addEventListener("input", updateFromForm);
});
document.getElementById("fEnchants").addEventListener("input", updateFromForm);

// material change from picker
document.getElementById("btnPickMaterial").addEventListener("click", () => openPicker());
document.getElementById("btnClearItem").addEventListener("click", () => {
    if (selectedIdx < 0) return;
    items[selectedIdx] = { material: "STONE", amount: 1, page: 1, category: curCat };
    save(); renderAll(); fillForm();
});
document.getElementById("btnDeleteItem").addEventListener("click", () => {
    if (selectedIdx < 0) return;
    items.splice(selectedIdx, 1);
    selectedIdx = -1; editing = false;
    save(); renderAll();
});
document.getElementById("btnSaveItem").addEventListener("click", updateFromForm);

document.getElementById("shopName").addEventListener("input", (e) => {
    shopName = e.target.value;
    save();
});
document.getElementById("catalogToggle").addEventListener("change", (e) => {
    catalog = e.target.checked;
    save();
});
document.getElementById("catalogToggle").checked = catalog;
document.getElementById("btnNewCat").addEventListener("click", () => {
    let n = 2;
    while (cats.indexOf("Category " + n) !== -1) n++;
    const name = "Category " + n;
    cats.push(name);
    curCat = name;
    save();
    renderAll();
    toast("New category '" + name + "' added");
});

// ================= picker =================
const overlay = document.getElementById("overlay");
function openPicker() {
    pickQuery = "";
    pickFilterCat = 0;
    document.getElementById("search").value = "";
    overlay.classList.add("open");
    renderPicker();
}
document.getElementById("searchClose").addEventListener("click", () => overlay.classList.remove("open"));
document.getElementById("search").addEventListener("input", (e) => { pickQuery = e.target.value.trim().toLowerCase(); renderPicker(); });
window.addEventListener("keydown", (e) => { if (e.key === "Escape") overlay.classList.remove("open"); });

function renderPicker() {
    const catsEl = document.getElementById("pickerCats");
    catsEl.innerHTML = "";
    CREATIVE_CATS.forEach((c, i) => {
        const t = document.createElement("div");
        t.className = "tab" + (i === pickFilterCat ? " active" : "");
        t.textContent = c;
        t.addEventListener("click", () => { pickFilterCat = i; renderPicker(); });
        catsEl.appendChild(t);
    });

    const g = document.getElementById("pickerGrid");
    g.innerHTML = "";
    let list = ITEMS.filter(it => it[2] === pickFilterCat);
    if (pickQuery) {
        list = ITEMS.filter(it => (it[0] + " " + it[1]).toLowerCase().indexOf(pickQuery) !== -1);
    }
    if (!list.length) {
        const d = document.createElement("div");
        d.textContent = "No items found";
        d.style.cssText = "grid-column:1/-1;text-align:center;font-size:8px;color:#888;padding:20px;";
        g.appendChild(d);
        return;
    }
    list.forEach(it => {
        const s = document.createElement("div");
        s.className = "slot";
        s.style.background = "rgba(0,0,0,0.4)";
        s.appendChild(iconEl(it[0]));
        const t = document.createElement("div");
        t.className = "slot-tag";
        t.textContent = it[1] || it[0];
        s.appendChild(t);
        s.title = (it[1] || it[0]) + " (" + it[0] + ")";
        s.addEventListener("click", () => {
            if (editing && selectedIdx >= 0) {
                items[selectedIdx].material = it[0];
                save();
                updateFromForm();
                overlay.classList.remove("open");
            }
        });
        g.appendChild(s);
    });
}

// ================= version templates =================
// Templates are generated from the plugin's curated catalog (templates.js).
// Picking a version replaces the editor with that version's curated items and
// turns on catalog:true, so the plugin tops up every remaining material the
// running server has when the file is loaded there.
const TEMPLATE_CAT_NAMES = {
    building: "Building",
    tools: "Tools & Weapons",
    armor: "Armor & Combat",
    food: "Food",
    ores: "Ores & Materials",
    redstone: "Redstone & Mechanics",
    decoration: "Decoration",
    transport: "Transport",
    nature: "Nature",
    brewing: "Brewing & Alchemy",
    enchanted: "Enchanted",
    misc: "Misc",
};

function seedVersionTemplate() {
    const sel = document.getElementById("versionTemplate");
    const minor = sel.value;
    if (!minor) return;
    const rows = (window.MINTED_TEMPLATES || {})[minor];
    if (!rows || !rows.length) { toast("No template data for 1." + minor, true); return; }
    let next = [];
    rows.forEach(row => {
        const it = {
            material: normMaterial(row.m),
            amount: 1,
            buy: row.b,
            sell: row.s,
            category: TEMPLATE_CAT_NAMES[row.c] || row.c || cats[0],
        };
        next.push(clean(it));
    });
    items = next;
    cats = [];
    items.forEach(it => { if (it.category && cats.indexOf(it.category) === -1) cats.push(it.category); });
    if (!cats.length) cats = ["General"];
    curCat = cats[0];
    catalog = true;
    document.getElementById("catalogToggle").checked = true;
    selectedIdx = -1; editing = false;
    save();
    renderAll();
    document.getElementById("templateHint").textContent = "1." + minor + " template: " + items.length + " curated items + catalog:true";
    document.getElementById("templateHint").style.display = "";
    toast("1." + minor + " template loaded (" + items.length + " items)");
}

// populate the version dropdown once templates.js loaded
(function () {
    const sel = document.getElementById("versionTemplate");
    const templates = window.MINTED_TEMPLATES || {};
    Object.keys(templates).forEach(minor => {
        const opt = document.createElement("option");
        opt.value = minor;
        opt.textContent = "1." + minor;
        sel.appendChild(opt);
    });
    sel.addEventListener("change", seedVersionTemplate);
})();

// ================= YAML =================
const esc = (s) => String(s).replace(/\\/g, "\\\\").replace(/"/g, '\\"');

function generateYaml() {
    let y = "shops:\n";
    y += "  - name: \"" + esc(shopName || "Global Shop") + "\"\n";
    y += "    catalog: " + (catalog ? "true" : "false") + "\n";
    y += "    currency: wallet\n";
    y += "    icon:\n";
    y += "      material: CHEST\n";
    y += "    items:\n";
    if (!items.length) { y += "      []\n"; return y; }
    items.forEach(it => {
        y += "      - material: " + (it.material || "STONE") + "\n";
        y += "        amount: " + amtNum(it) + "\n";
        if (it.name) y += "        name: \"" + esc(it.name) + "\"\n";
        if (it.lore && it.lore.length) {
            y += "        lore:\n";
            it.lore.forEach(l => y += "          - \"" + esc(l) + "\"\n");
        }
        if (it.enchants && Object.keys(it.enchants).length) {
            y += "        enchants:\n";
            Object.entries(it.enchants).forEach(([k, v]) => y += "          " + k + ": " + v + "\n");
        }
        if (it.buy !== undefined) y += "        buy: " + it.buy + "\n";
        if (it.sell !== undefined) y += "        sell: " + it.sell + "\n";
        if (it.category) y += "        category: \"" + esc(it.category) + "\"\n";
        if (it.page) y += "        page: " + it.page + "\n";
        if (it.slot !== undefined) y += "        slot: " + it.slot + "\n";
        if (it.data) y += "        data: " + it.data + "\n";
    });
    return y;
}

function download(name, content) {
    const blob = new Blob([content], { type: "text/yaml" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = name;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
    toast("global-shops.yml downloaded");
}
document.getElementById("btnDownload").addEventListener("click", () => download("global-shops.yml", generateYaml()));
document.getElementById("btnImport").addEventListener("click", () => document.getElementById("fileImport").click());
document.getElementById("fileImport").addEventListener("change", onImport);

// plugin material keys -> web ITEMS keys (legacy aliases / renames)
const MAT_REMAP = {
    LAPIS: "LAPIS_LAZULI",
    MELON: "MELON_SLICE",
    MELON_BLOCK: "MELON_SLICE",
    GLISTERING_MELON: "GLISTERING_MELON_SLICE",
    BOAT: "OAK_BOAT",
    TOTEM: "TOTEM_OF_UNDYING",
    COOKED_BEEF: "STEAK",
};
const normMaterial = (m) => {
    let k = String(m || "STONE").trim();
    if (/^minecraft:/i.test(k)) k = k.slice(10);
    k = k.toUpperCase().replace(/[.\s-]/g, "_");
    return MAT_REMAP[k] || k;
};

// import: take the FIRST shop's items (single global shop)
function onImport(e) {
    const file = e.target.files[0];
    if (!file) return;
    const r = new FileReader();
    r.onload = () => {
        try {
            const shops = parseYaml(r.result);
            if (!shops.length) { toast("No shops found in the file", true); return; }
            const shop = shops[0];
            shopName = shop.name || "Global Shop";
            catalog = shop.catalog !== undefined ? !!shop.catalog : true;
            items = (shop.items || []).map(normItem).filter(Boolean);
            cats = [];
            items.forEach(it => { if (it.category && cats.indexOf(it.category) === -1) cats.push(it.category); });
            if (!cats.length) cats = ["General"];
            curCat = cats[0];
            selectedIdx = -1; editing = false;
            save();
            renderAll();
            toast("Imported " + items.length + " item(s)");
        } catch (err) {
            toast("Parse error: " + err.message, true);
        }
        e.target.value = "";
    };
    r.readAsText(file);
}
const normItem = (it) => {
    if (!it || typeof it !== "object") return null;
    const out = { material: normMaterial(it.material || it.type), amount: num(it.amount, 1) };
    if (it.name) out.name = String(it.name);
    if (it.lore && it.lore.length) out.lore = it.lore.map(String);
    if (it.buy !== undefined && it.buy !== null) out.buy = Number(it.buy);
    if (it.sell !== undefined && it.sell !== null) out.sell = Number(it.sell);
    if (it.category) out.category = String(it.category);
    if (it.page !== undefined && it.page !== null) out.page = Number(it.page);
    if (it.slot !== undefined && it.slot !== null) out.slot = Number(it.slot);
    if (it.data) out.data = Number(it.data);
    if (it.enchants && typeof it.enchants === "object") {
        const en = {};
        Object.keys(it.enchants).forEach(k => en[k.toLowerCase()] = Number(it.enchants[k]));
        out.enchants = en;
    }
    return clean(out);
};

// yaml reader handling BOTH the web generator's indented style AND the
// plugin's SnakeYAML "indentless list" style (dash at the parent key's column).
function parseYaml(text) {
    const lines = [];
    text.split(/\r?\n/).forEach(raw => {
        const m = raw.match(/^(\s*)(.*?)\s*$/);
        if (!m) return;
        const t = m[2];
        if (!t || t.startsWith("#")) return;
        lines.push({ indent: m[1].length, t: t });
    });
    let i = 0;
    const peek = () => lines[i];
    const scalar = (s) => {
        s = s.trim();
        if (s === "") return "";
        if (s[0] === '"' && s[s.length - 1] === '"') return s.slice(1, -1).replace(/\\"/g, '"');
        if (s[0] === "'" && s[s.length - 1] === "'") return s.slice(1, -1).replace(/''/g, "'");
        if (s === "[]") return [];
        if (s === "{}") return {};
        if (s === "null" || s === "~") return null;
        if (s === "true") return true;
        if (s === "false") return false;
        if (/^-?\d+(\.\d+)?$/.test(s)) return Number(s);
        return s;
    };
    const entry = (l) => { // "key: rest" | "key:" | plain
        const c = l.t.indexOf(":");
        if (c === -1) return null;
        return { key: l.t.slice(0, c).trim(), rest: l.t.slice(c + 1).trim() };
    };
    function parseValueChild(keyIndent) {
        const l = peek();
        if (!l) return null;
        if (l.t === "[]") { i++; return []; }
        if (l.t === "{}") { i++; return {}; }
        if (l.t.startsWith("- ")) return l.indent === keyIndent ? parseSeq(keyIndent) : parseAt(l.indent);
        if (l.indent > keyIndent) return parseAt(l.indent);
        return null;
    }
    function parseAt(indent) {
        const l = peek();
        if (!l) return null;
        if (l.t === "[]") { i++; return []; }
        if (l.t === "{}") { i++; return {}; }
        if (l.t.startsWith("- ")) return parseSeq(indent);
        return parseMap(indent);
    }
    function parseMap(indent) {
        const out = {};
        while (i < lines.length) {
            const l = peek();
            if (l.indent !== indent || l.t.startsWith("- ")) break;
            const e = entry(l);
            if (!e) { i++; continue; }
            i++;
            out[e.key] = e.rest === "" ? parseValueChild(indent) : scalar(e.rest);
        }
        return out;
    }
    function parseSeq(indent) {
        const out = [];
        while (i < lines.length) {
            const l = peek();
            if (l.indent !== indent || !l.t.startsWith("- ")) break;
            const rest = l.t.slice(2).trim();
            i++;
            const c = rest.indexOf(":");
            if (c === -1) { out.push(scalar(rest)); continue; }
            const item = {};
            const k0 = rest.slice(0, c).trim();
            const v0 = rest.slice(c + 1).trim();
            item[k0] = v0 === "" ? parseValueChild(indent) : scalar(v0);
            // remaining fields of this list element sit deeper than the dash
            while (i < lines.length) {
                const f = peek();
                if (f.indent <= indent || f.t.startsWith("- ")) break;
                const e = entry(f);
                if (!e) { i++; continue; }
                i++;
                item[e.key] = e.rest === "" ? parseValueChild(f.indent) : scalar(e.rest);
            }
            out.push(item);
        }
        return out;
    }
    const root = parseAt(0);
    if (Array.isArray(root)) return root;
    if (root && Array.isArray(root.shops)) return root.shops;
    return [];
}

// ================= toast =================
let toastEl = null;
function toast(msg, isErr) {
    if (toastEl) toastEl.remove();
    toastEl = document.createElement("div");
    toastEl.style.cssText =
        "position:fixed;bottom:18px;left:50%;transform:translateX(-50%);" +
        "font-family:var(--font-pixel);font-size:9px;padding:12px 18px;" +
        "background:rgba(0,0,0,0.92);border:2px solid " + (isErr ? "#c00" : "#00d800") + ";" +
        "color:#fff;z-index:200;box-shadow:4px 4px 0 rgba(0,0,0,0.6);text-shadow:1px 1px 0 #000;" +
        "animation:toast-in 0.16s steps(3) both;max-width:90vw;text-align:center;";
    toastEl.textContent = msg;
    document.body.appendChild(toastEl);
    setTimeout(() => {
        toastEl.style.transition = "opacity 0.24s steps(3), transform 0.24s steps(3)";
        toastEl.style.opacity = "0";
        toastEl.style.transform = "translateX(-50%) translateY(8px)";
        setTimeout(() => toastEl.remove(), 260);
    }, 2400);
}

// ================= boot =================
renderAll();
window.addEventListener("beforeunload", save);