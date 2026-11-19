#!/usr/bin/env node
/**
 * Measures the web UI against a practical Material 3 Expressive conformance model.
 * Requires the dev server (`npm run dev`) and a system Chrome.
 *
 *   node scripts/audit-m3.mjs [url]
 *
 * The audit loads a local sample image, opens the style sheet, measures the DOM at six
 * viewports, and checks spacing, shape, type, touch targets, layout containment, and the
 * per-element watermark size controls. Exit code 1 means at least one violation.
 */
import { chromium } from 'playwright-core'
import { writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const url = process.argv[2] ?? 'http://127.0.0.1:5173/'
const chrome = process.env.CHROME_PATH ?? '/usr/bin/google-chrome'
const file = process.env.AUDIT_IMAGE ?? fileURLToPath(new URL('../docs/audit-sample.jpg', import.meta.url))
const SPACING = new Set([0, 4, 8, 12, 16, 20, 24, 32, 40, 48, 56, 64])
const SHAPES = new Set([0, 8, 12, 16, 20, 24, 28, 32, 9999])
const TYPE = new Set([12, 14, 16, 20, 22, 24, 28])
const ELEMENT_SLIDERS = ['文字全体', 'タイトル', 'サブタイトル', '座標・地名', '日付', '地図']
const MAP_ZOOM_SLIDER = '地図内部ズーム'
const VIEWPORTS = [
  { width: 320, height: 568, name: 'compact-320', touch: true },
  { width: 390, height: 844, name: 'mobile-390', touch: true },
  { width: 568, height: 320, name: 'phone-landscape-568', touch: true },
  { width: 768, height: 1024, name: 'tablet-768', touch: true },
  { width: 844, height: 390, name: 'phone-landscape-844', touch: true },
  { width: 1440, height: 1000, name: 'desktop-1440', touch: false },
]

/** Runs in the page: measured box plus the inset-expanded hit area of its ::after. */
const PAGE_HELPERS = `
  const measure = (el) => {
    if (!el) return null
    const r = el.getBoundingClientRect()
    const cs = getComputedStyle(el)
    return {
      x: Math.round(r.x), y: Math.round(r.y),
      w: Math.round(r.width), h: Math.round(r.height),
      padding: cs.padding, gap: cs.gap,
      radius: cs.borderRadius, fontSize: cs.fontSize,
    }
  }
  const target = (el) => {
    const r = el.getBoundingClientRect()
    const cs = getComputedStyle(el)
    const after = getComputedStyle(el, '::after')
    const left = Number.parseFloat(after.left) || 0
    const right = Number.parseFloat(after.right) || 0
    const topValue = Number.parseFloat(after.top) || 0
    const bottom = Number.parseFloat(after.bottom) || 0
    const borders =
      (Number.parseFloat(cs.borderLeftWidth) || 0) +
      (Number.parseFloat(cs.borderRightWidth) || 0)
    const verticalBorders =
      (Number.parseFloat(cs.borderTopWidth) || 0) +
      (Number.parseFloat(cs.borderBottomWidth) || 0)
    const expanded = after.content !== 'none' && after.position === 'absolute'
    return {
      tag: el.tagName,
      slot: el.getAttribute('data-slot'),
      type: el.getAttribute('type'),
      label:
        el.getAttribute('aria-label') ??
        el.querySelector('[role="slider"]')?.getAttribute('aria-label') ??
        el.textContent?.trim().slice(0, 32),
      x: Math.round(expanded ? r.x + (Number.parseFloat(cs.borderLeftWidth) || 0) + left : r.x),
      y: Math.round(expanded ? r.y + (Number.parseFloat(cs.borderTopWidth) || 0) + topValue : r.y),
      w: Math.round(expanded ? r.width - borders - left - right : r.width),
      h: Math.round(expanded ? r.height - verticalBorders - topValue - bottom : r.height),
      expanded,
    }
  }
  const visibleTargets = (root) =>
    [...root.querySelectorAll('button, input, select, [data-slot="slider"], [role="switch"]')]
      .filter((el) => {
        const cs = getComputedStyle(el)
        return cs.display !== 'none' && cs.visibility !== 'hidden' && el.getBoundingClientRect().width > 0
      })
      .map(target)
`

async function measureViewport(browser, width, height, touch) {
  const page = await browser.newPage({
    viewport: { width, height },
    deviceScaleFactor: touch ? 2 : 1,
    isMobile: width < 600,
    hasTouch: touch,
  })
  const errors = []
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text())
  })
  page.on('pageerror', (error) => errors.push(String(error)))

  await page.goto(url, { waitUntil: 'networkidle' })
  const run = (body) => page.evaluate(`(() => { ${PAGE_HELPERS}; ${body} })()`)
  const base = await run(`
    return {
      viewport: { width: innerWidth, height: innerHeight },
      coarse: matchMedia('(pointer: coarse)').matches,
      header: measure(document.querySelector('header')),
      headerTitle: measure(document.querySelector('header h1')),
      emptyCard: measure(document.querySelector('[data-testid="empty-card"]')),
      emptyTitle: measure(document.querySelector('main h2')),
      primaryCta: measure([...document.querySelectorAll('button')].find((el) => el.textContent?.includes('写真・動画を選ぶ'))),
      secondaryCta: measure([...document.querySelectorAll('button')].find((el) => el.textContent?.includes('カメラで撮影する'))),
      headerInner: measure(document.querySelector('[data-testid="app-bar-inner"]')),
      documentHeight: document.documentElement.scrollHeight,
      targets: visibleTargets(document.body),
      overflow: document.documentElement.scrollWidth - innerWidth,
    }
  `)

  await page.setInputFiles('input[type=file]', file)
  await page.waitForTimeout(3500)
  const editor = await run(`
    const stage = document.querySelector('canvas[aria-label^="透かし"]')?.parentElement
    const toolbar = document.querySelector('[aria-label="保存"]')?.closest('div.pointer-events-auto')
    const title = document.querySelector('header h1')
    const leftButtons = ['別の写真・動画を選択', 'カメラ']
      .map((label) => document.querySelector('header button[aria-label="' + label + '"]'))
      .filter(Boolean)
    const slot = document.querySelector('[data-testid="stage-slot"]')
    const slotStyle = slot ? getComputedStyle(slot) : null
    return {
      stage: measure(stage),
      stageSlot: slotStyle
        ? { top: slotStyle.top, right: slotStyle.right, bottom: slotStyle.bottom, left: slotStyle.left }
        : null,
      headerTitle: measure(title),
      leftIconRight: leftButtons.length
        ? Math.max(...leftButtons.map((el) => el.getBoundingClientRect().right))
        : 0,
      toolbar: measure(toolbar),
      hint: (() => {
        const el = document.querySelector('main p')
        const cs = el ? getComputedStyle(el) : null
        return cs ? { fontSize: cs.fontSize } : null
      })(),
      targets: visibleTargets(document.body),
      overflow: document.documentElement.scrollWidth - innerWidth,
    }
  `)

  await page.getByRole('button', { name: 'スタイル' }).click()
  await page.waitForTimeout(500)
  const sheet = await run(`
    const content = document.querySelector('[data-slot="sheet-content"]')
    const stage = document.querySelector('canvas[aria-label^="透かし"]')?.parentElement
    const sliders = [...content.querySelectorAll('[data-slot="slider"]')].map((root) => {
      const thumb = root.querySelector('[role="slider"]')
      return {
        label: thumb?.getAttribute('aria-label') ?? null,
        value: Number(thumb?.getAttribute('aria-valuenow')),
        min: Number(thumb?.getAttribute('aria-valuemin')),
        max: Number(thumb?.getAttribute('aria-valuemax')),
        valueText: thumb?.getAttribute('aria-valuetext') ?? null,
        box: measure(root),
        hit: thumb ? target(thumb) : null,
      }
    })
    return {
      content: measure(content),
      side: content.getAttribute('data-side'),
      appTitle: measure(document.querySelector('header h1')),
      header: measure(content.querySelector('[data-testid="sheet-header-bar"]')),
      body: measure(content.querySelector('[data-testid="sheet-body"]')),
      title: measure(content.querySelector('[data-slot="sheet-title"]')),
      titleRow: measure(content.querySelector('[data-testid="sheet-title-row"]')),
      close: measure(content.querySelector('button[aria-label="閉じる"]')),
      select: measure(content.querySelector('[data-slot="select-trigger"]')),
      presetCard: measure(content.querySelector('[data-testid="preset-card"]')),
      card: measure(content.querySelector('[data-testid="section-card"]')),
      cardHeader: measure(content.querySelector('[data-testid="section-card-header"]')),
      cardBody: measure(content.querySelector('[data-testid="section-card-body"]')),
      stage: measure(stage),
      toolbarVisible: Boolean(document.querySelector('[aria-label="保存"]')),
      sliders,
      targets: visibleTargets(content),
      overflow: document.documentElement.scrollWidth - innerWidth,
    }
  `)
  await page.getByRole('combobox', { name: 'フォント' }).click()
  await page.waitForTimeout(250)
  sheet.selectOptions = await run(`
    return [...document.querySelectorAll('[data-slot="select-item"]')].map(target)
  `)
  await page.keyboard.press('Escape')
  await page.waitForTimeout(100)
  const errorsAfter = [...errors]
  await page.close()
  return { base, editor, sheet, errors: errorsAfter }
}

async function verifySheetFocus(browser) {
  const results = {}
  for (const [width, height, name] of [
    [320, 568, 'bottom'],
    [568, 320, 'compact-side'],
  ]) {
    const page = await browser.newPage({
      viewport: { width, height },
      deviceScaleFactor: 2,
      isMobile: width < 768,
      hasTouch: true,
    })
    await page.goto(url, { waitUntil: 'networkidle' })
    await page.setInputFiles('input[type=file]', file)
    await page.waitForTimeout(3000)
    const style = page.getByRole('button', { name: 'スタイル' })
    await style.focus()
    await style.click()
    await page.waitForTimeout(400)
    await page.keyboard.press('Escape')
    await page.waitForTimeout(800)
    results[name] = await page.evaluate(() => document.activeElement?.getAttribute('aria-label') ?? null)
    await page.close()
  }
  return results
}

async function verifyReducedMotion(browser) {
  const page = await browser.newPage({
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 2,
    isMobile: true,
    hasTouch: true,
    reducedMotion: 'reduce',
  })
  await page.goto(url, { waitUntil: 'domcontentloaded' })
  const result = await page.evaluate(() => {
    const element = document.querySelector('button')
    const style = element ? getComputedStyle(element) : null
    return {
      matches: matchMedia('(prefers-reduced-motion: reduce)').matches,
      transitionDuration: style?.transitionDuration ?? null,
      animationDuration: style?.animationDuration ?? null,
    }
  })
  await page.close()
  return result
}

async function verifyElementControls(browser) {
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } })
  const errors = []
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text())
  })
  page.on('pageerror', (error) => errors.push(String(error)))
  await page.goto(url, { waitUntil: 'networkidle' })
  await page.setInputFiles('input[type=file]', file)
  await page.waitForTimeout(3500)
  await page.getByRole('button', { name: /NH 1731/ }).click()
  await page.waitForTimeout(300)
  const locationName = page.locator('#location-name')
  if (await locationName.count()) await locationName.fill('Osaka, Japan')
  await page.getByRole('button', { name: '適用' }).click()
  await page.waitForTimeout(300)
  await page.getByRole('button', { name: 'スタイル' }).click()
  await page.waitForTimeout(400)
  const canvas = page.locator('canvas[aria-label^="透かし"]')
  const snapshot = () => canvas.evaluate((element) => element.toDataURL())
  const reset = page.getByRole('button', { name: '位置・サイズをリセット' })
  await reset.click()
  await page.waitForTimeout(400)
  const baseline = await snapshot()
  const results = {}
  for (const label of ELEMENT_SLIDERS) {
    const slider = page.getByRole('slider', { name: label, exact: true })
    await slider.focus()
    await slider.press('End')
    await page.waitForTimeout(350)
    results[label] = {
      changed: (await snapshot()) !== baseline,
      value: await slider.getAttribute('aria-valuenow'),
    }
    await reset.click()
    await page.waitForTimeout(300)
    results[label].resetValue = await slider.getAttribute('aria-valuenow')
  }
  const titleSlider = page.getByRole('slider', { name: 'タイトル', exact: true })
  await titleSlider.focus()
  await titleSlider.press('Home')
  await titleSlider.press('ArrowRight')
  await page.waitForTimeout(200)
  const stepValue = await titleSlider.getAttribute('aria-valuenow')
  await titleSlider.press('End')
  await page.waitForTimeout(300)
  const persisted = await page.evaluate(() => {
    const raw = localStorage.getItem('sukashi-map-web')
    return raw ? JSON.parse(raw)?.state?.style?.titleScale ?? null : null
  })
  await page.getByRole('button', { name: '閉じる' }).click()
  await page.waitForTimeout(200)
  await page.getByRole('button', { name: 'その他の保存メニュー' }).click()
  await page.waitForTimeout(200)
  const menuItems = await page.evaluate(() =>
    [...document.querySelectorAll('[data-slot="dropdown-menu-item"]')].map((element) => {
      const rect = element.getBoundingClientRect()
      return { label: element.textContent?.trim(), h: Math.round(rect.height) }
    }),
  )
  await page.keyboard.press('Escape')
  await page.close()
  return { results, persisted, stepValue, menuItems, errors }
}

const browser = await chromium.launch({
  executablePath: chrome,
  headless: true,
  args: ['--no-sandbox', '--disable-gpu'],
})
const results = []
for (const viewport of VIEWPORTS) {
  results.push({
    name: viewport.name,
    ...(await measureViewport(browser, viewport.width, viewport.height, viewport.touch)),
  })
}
const elementControls = await verifyElementControls(browser)
const reducedMotion = await verifyReducedMotion(browser)
const sheetFocus = await verifySheetFocus(browser)
await browser.close()

const findings = []
function checkSpacing(name, value) {
  if (!value) return
  const nums = value.match(/[\d.]+/g)?.map(Number) ?? []
  for (const n of nums) {
    if (!SPACING.has(n)) findings.push(`${name}: spacing ${n}px is outside the 4dp grid`)
  }
}
function checkType(name, value) {
  if (!value) return
  const n = Number.parseFloat(value)
  if (!TYPE.has(n)) findings.push(`${name}: font ${n}px is outside the M3 type roles`)
}
function checkShape(name, value) {
  if (!value) return
  const n = Number.parseFloat(value)
  const normalized = n >= 9999 ? 9999 : n
  if (!SHAPES.has(normalized)) findings.push(`${name}: radius ${n}px is outside the shape scale`)
}

for (const result of results) {
  const name = result.name
  const touch = result.base.coarse
  const minTarget = touch ? 44 : 40
  const stage = result.sheet.stage ?? result.editor.stage
  const toolbar = result.editor.toolbar

  for (const [label, value] of [
    ['header', result.base.header],
    ['app-bar inner', result.base.headerInner],
    ['app title', result.base.headerTitle],
    ['empty card', result.base.emptyCard],
    ['empty title', result.base.emptyTitle],
    ['primary CTA', result.base.primaryCta],
    ['secondary CTA', result.base.secondaryCta],
    ['editor stage', result.editor.stage],
    ['sheet content', result.sheet.content],
    ['sheet header', result.sheet.header],
    ['sheet body', result.sheet.body],
    ['sheet title', result.sheet.title],
    ['sheet close', result.sheet.close],
    ['sheet select', result.sheet.select],
  ]) {
    if (!value) findings.push(`${name}: required ${label} is missing`)
  }

  if (result.base.documentHeight > result.base.viewport.height + 1) {
    findings.push(`${name}: base screen scrolls vertically by ${result.base.documentHeight - result.base.viewport.height}px`)
  }
  for (const [label, cta] of [['primary', result.base.primaryCta], ['secondary', result.base.secondaryCta]]) {
    if (cta && (cta.y < result.base.header.h - 1 || cta.y + cta.h > result.base.viewport.height + 1)) {
      findings.push(`${name}: ${label} CTA is outside the visible viewport`)
    }
  }
  if (stage && (stage.w < 100 || stage.h < 70)) {
    findings.push(`${name}: watermark stage is too small (${stage.w}x${stage.h})`)
  }
  if (toolbar && (toolbar.x < -1 || toolbar.x + toolbar.w > result.base.viewport.width + 1)) {
    findings.push(`${name}: floating toolbar is clipped by the viewport`)
  }
  if (result.editor.headerTitle?.w > 0 && result.editor.headerTitle.x < result.editor.leftIconRight - 1) {
    findings.push(`${name}: app-bar title overlaps the left action icons`)
  }
  if (result.editor.stageSlot) {
    for (const edge of ['top', 'right', 'bottom', 'left']) {
      const n = Number.parseFloat(result.editor.stageSlot[edge])
      if (!SPACING.has(n)) findings.push(`${name}: stage inset ${edge}=${n}px is outside the 4dp grid`)
    }
  }
  for (const option of result.sheet.selectOptions ?? []) {
    if (option.h < minTarget) findings.push(`${name}: font option ${option.label} is ${option.w}x${option.h} < ${minTarget}`)
  }
  const switchTargets = result.sheet.targets.filter((target) => target.slot === 'switch')
  for (let i = 0; i < switchTargets.length; i++) {
    for (let j = i + 1; j < switchTargets.length; j++) {
      const a = switchTargets[i]
      const b = switchTargets[j]
      if (a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y) {
        findings.push(`${name}: switch hit boxes overlap (${a.label}, ${b.label})`)
      }
    }
  }

  if (result.base.overflow > 1) findings.push(`${name}: page overflows horizontally by ${result.base.overflow}px`)
  if (result.editor.overflow > 1) findings.push(`${name}: editor overflows horizontally by ${result.editor.overflow}px`)
  if (result.sheet.overflow > 1) findings.push(`${name}: open sheet overflows horizontally by ${result.sheet.overflow}px`)

  for (const target of [...result.base.targets, ...result.editor.targets, ...result.sheet.targets]) {
    if (target.w < minTarget || target.h < minTarget) {
      findings.push(`${name}: target ${target.label ?? target.tag} is ${target.w}x${target.h} < ${minTarget}`)
    }
  }

  if (stage && toolbar && result.sheet.toolbarVisible) {
    const stageBottom = stage.y + stage.h
    if (stageBottom > toolbar.y) findings.push(`${name}: watermark stage overlaps the floating toolbar`)
    if (stage.y < result.base.header.h) findings.push(`${name}: watermark stage starts under the app bar`)
  }

  const sheetContent = result.sheet.content
  const sideSheet = sheetContent != null && result.sheet.side === 'right'
  if (sideSheet) {
    if (Math.abs(sheetContent.x + sheetContent.w - result.base.viewport.width) > 1 || Math.abs(sheetContent.y) > 1) {
      findings.push(`${name}: side sheet is not anchored to the right edge`)
    }
    if (stage && stage.x + stage.w > sheetContent.x) {
      findings.push(`${name}: watermark stage is hidden by the side sheet`)
    }
    const title = result.sheet.appTitle
    if (title && title.w > 0 && title.x + title.w > sheetContent.x) {
      findings.push(`${name}: app-bar title is clipped by the side sheet`)
    }
  } else if (sheetContent) {
    const sheetBottom = sheetContent.y + sheetContent.h
    if (Math.abs(sheetBottom - result.base.viewport.height) > 1) {
      findings.push(`${name}: compact sheet is not bottom-anchored`)
    }
    if (sheetContent.w > result.base.viewport.width + 1) {
      findings.push(`${name}: compact sheet exceeds the viewport width`)
    }
    if (sheetContent.h > result.base.viewport.height * 0.5 + 1) {
      findings.push(`${name}: compact sheet is taller than 50dvh`)
    }
    if (stage && stage.y + stage.h > sheetContent.y) {
      findings.push(`${name}: watermark stage is covered by the compact sheet`)
    }
    if (sheetContent.x !== 0) findings.push(`${name}: bottom sheet is not anchored to the left edge`)
  }

  checkSpacing(`${name} header padding`, result.base.header?.padding)
  checkSpacing(`${name} app-bar inner padding`, result.base.headerInner?.padding)
  checkSpacing(`${name} sheet header padding`, result.sheet.header?.padding)
  checkSpacing(`${name} sheet title row gap`, result.sheet.titleRow?.gap)
  checkSpacing(`${name} sheet header gap`, result.sheet.header?.gap)
  checkSpacing(`${name} sheet body padding`, result.sheet.body?.padding)
  checkSpacing(`${name} section card header padding`, result.sheet.cardHeader?.padding)
  checkSpacing(`${name} section card header gap`, result.sheet.cardHeader?.gap)
  checkSpacing(`${name} section card body padding`, result.sheet.cardBody?.padding)
  checkSpacing(`${name} section card body gap`, result.sheet.cardBody?.gap)
  checkSpacing(`${name} editor toolbar padding`, toolbar?.padding)
  checkSpacing(`${name} editor toolbar gap`, toolbar?.gap)

  checkType(`${name} app title`, result.base.headerTitle?.fontSize)
  checkType(`${name} empty headline`, result.base.emptyTitle?.fontSize)
  checkType(`${name} sheet title`, result.sheet.title?.fontSize)
  checkType(`${name} sheet select`, result.sheet.select?.fontSize)
  checkType(`${name} hint`, result.editor.hint?.fontSize)

  checkShape(`${name} empty card`, result.base.emptyCard?.radius)
  checkShape(`${name} stage`, result.editor.stage?.radius)
  checkShape(`${name} sheet card`, result.sheet.card?.radius)
  checkShape(`${name} sheet content`, result.sheet.content?.radius)
  checkShape(`${name} sheet close`, result.sheet.close?.radius)
  checkShape(`${name} sheet select`, result.sheet.select?.radius)
  checkShape(`${name} preset card`, result.sheet.presetCard?.radius)

  const labels = result.sheet.sliders.map((slider) => slider.label)
  for (const expected of ELEMENT_SLIDERS) {
    if (!labels.includes(expected)) findings.push(`${name}: missing ${expected} percentage control`)
  }
  const zoomSlider = result.sheet.sliders.find((slider) => slider.label === MAP_ZOOM_SLIDER)
  if (!zoomSlider) findings.push(`${name}: missing ${MAP_ZOOM_SLIDER} control`)
  else if (zoomSlider.min !== 0.5 || zoomSlider.max !== 5) {
    findings.push(`${name}: ${MAP_ZOOM_SLIDER} range is ${zoomSlider.min}–${zoomSlider.max}, expected 0.5–5`)
  }
  for (const slider of result.sheet.sliders) {
    if (ELEMENT_SLIDERS.includes(slider.label) && (slider.min !== 0.5 || slider.max !== 2.5)) {
      findings.push(`${name}: ${slider.label} range is ${slider.min}–${slider.max}, expected 0.5–2.5`)
    }
    if (slider.hit && (slider.hit.w < minTarget || slider.hit.h < minTarget)) {
      findings.push(`${name}: ${slider.label} thumb hit area ${slider.hit.w}x${slider.hit.h} < ${minTarget}`)
    }
    const expectedText = ELEMENT_SLIDERS.includes(slider.label)
      ? `${Math.round(slider.value * 100)}%`
      : `${slider.value.toFixed(2)}×`
    if (slider.valueText !== expectedText) {
      findings.push(`${name}: ${slider.label} aria-valuetext is ${slider.valueText}, expected ${expectedText}`)
    }
  }

  if (result.errors.length) {
    findings.push(`${name}: console/page errors: ${result.errors.slice(0, 2).join(' | ')}`)
  }
}

for (const [label, result] of Object.entries(elementControls.results)) {
  if (!result.changed) findings.push(`element control ${label} did not change the rendered canvas`)
  if (Number(result.value) !== 2.5) findings.push(`element control ${label} did not reach 250% (${result.value})`)
  if (Number(result.resetValue) !== 1) findings.push(`element control ${label} did not reset to 100% (${result.resetValue})`)
}
if (Number(elementControls.persisted) !== 2.5) {
  findings.push(`element control titleScale did not persist at 250% (${elementControls.persisted})`)
}
if (Number(elementControls.stepValue) !== 0.51) {
  findings.push(`element control step is ${elementControls.stepValue}, expected 0.01`)
}
for (const item of elementControls.menuItems ?? []) {
  if (item.h < 40) findings.push(`menu item ${item.label} is ${item.h}px < 40px on desktop`)
}
if (elementControls.errors.length) {
  findings.push(`element control check: page errors: ${elementControls.errors.slice(0, 2).join(' | ')}`)
}
for (const [surface, label] of Object.entries(sheetFocus)) {
  if (label !== 'スタイル') findings.push(`sheet focus ${surface} returned to ${label ?? 'body'}, expected スタイル`)
}
if (!reducedMotion.matches) {
  findings.push('reduced-motion media query did not match in the emulated browser')
} else {
  for (const key of ['transitionDuration', 'animationDuration']) {
    const value = reducedMotion[key]
    const ms = value ? Number.parseFloat(value) * (value.endsWith('ms') ? 1 : 1000) : 0
    if (ms > 1) findings.push(`reduced motion ${key} is ${value}, expected ≤1ms`)
  }
}

const report = JSON.stringify({ findings, results, elementControls, reducedMotion, sheetFocus }, null, 2)
writeFileSync(fileURLToPath(new URL('../docs/m3-audit-latest.json', import.meta.url)), `${report}\n`)
console.log(report)
if (findings.length) {
  console.error(`\nM3 audit: ${findings.length} violation(s)`)
  process.exitCode = 1
} else {
  console.error('M3 audit: PASS')
}
