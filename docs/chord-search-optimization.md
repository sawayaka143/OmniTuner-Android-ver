# Ускорение поиска аппликатур аккордов: перенос из Android в веб-версию OmniTuner

Документ для агента, который будет переносить оптимизацию в веб-приложение
(`C:\Projects\OmniTuner`, Angular + TypeScript). Источник — Android-порт, коммит
`5680495` («Chord engine fixes … voicing search prunes span, finger groups and stranded
mutes …»), файл `shared/src/commonMain/kotlin/com/omnituner/core/theory/ChordVoicing.kt`.

## 0. Коротко

- Веб-файл `src/app/utils/chord-voicing.ts` сейчас **в точности** соответствует
  Android-версии *до* оптимизации (тот же `VoicingSearch`, те же веса, тот же `walk`).
  Поэтому перенос — это механическое применение одного диффа, а не переписывание.
- Ускорение получено **не** микрооптимизациями, а тем, что перебор перестал заходить в
  ветки, которые заведомо дают неиграбельную аппликатуру. Раньше такие ветки
  перебирались до конца и отбрасывались только в `isPlayable`.
- Замеры после оптимизации (записаны в сообщении коммита, JVM, `count = 12`):
  **6 струн — меньше 25 мс, 8 струн — меньше 0,3 с, 12 струн — меньше 1,6 с.**
  Время «до» в коммите не записано; на 7+ струнах это были секунды (рост
  экспоненциальный: на каждую струну ×(1 + число подходящих ладов)).
- Изменения в итоговых результатах для 6-струнной гитары практически нулевые: три из
  пяти правок — точные отсечения (выкидывают только то, что `isPlayable` всё равно
  отбросил бы), две — сознательные ограничения, которые убирают аппликатуры, никогда не
  попадающие в топ.

## 1. Как устроен поиск (общая схема, одинакова в обеих версиях)

1. `stringOptions()` — для каждой струны список вариантов: `null` (заглушена) + лады
   `0..MAX_FRET (12)`, чья нота входит в аккорд (`allowedPcs`, плюс басовая нота для
   слэш-аккорда).
2. `enumerate()` — рекурсивный `walk(idx, …)` идёт от самой низкой струны (индекс 0) к
   самой высокой и на каждой пробует все варианты. Полная аппликатура → `visit()`.
3. В `run()` каждая аппликатура проходит `describeShape` (признаки + `analyseFingering`),
   `isPlayable` (все обязательные ноты, бас, растяжка, глушение), получает `score`.
4. Сортировка `compareRanked` и `pickDistinct` (выкидывает аппликатуры, которые являются
   лучшей с заглушенными струнами).
5. `searchChord` повторяет поиск с ослаблением правил: (бас + расширения) →
   (бас без расширений) → (без баса).

Отсечения, которые **уже были** в `walk` до оптимизации (оставить как есть):

- если какую-то обязательную ноту уже не может дать ни одна из оставшихся струн
  (`reachable[idx]`) — выход;
- если даже при игре на всех оставшихся струнах не набрать `MIN_NOTES = 3` — выход;
- если не набрать `MIN_DISTINCT = 3` разных нот аккорда — выход.

Проблема: эти отсечения смотрят только на **ноты**, но не на **руку**. Почти все
кандидаты на длинных строях отбрасывались из-за растяжки, числа пальцев и
«висящих» заглушённых струн — но только после полного перебора всех верхних струн.

## 2. Пять оптимизаций

### 2.1. `handFits` — отсечение по руке на префиксе (главное ускорение)

После выбора лада на струне `idx` проверяем, может ли рука в принципе удержать уже
выбранные струны `0..idx`. Если нет — ни одно продолжение не будет играбельным, потому
что добавление струн только **увеличивает** растяжку и число пальцев. Ветку бросаем.

Две части:

- **`fingerGroups(prefix)`** — нижняя граница числа пальцев: одна группа на каждую серию
  нот на одном ладу, которую можно взять баррэ. Нота присоединяется к группе, если,
  идя вниз по струнам, мы встречаем тот же лад раньше, чем открытую струну или более
  низкий лад (заглушенные и более высокие лады баррэ не прерывают — ровно те же правила,
  что в `analyseFingering`). Более поздние струны никогда не сливают ранние группы,
  поэтому счётчик монотонен. `analyseFingering` в лучшем случае сводит пальцы к числу
  групп, значит `groups > 4` ⇒ `analyseFingering` вернёт `null`. **Точное отсечение.**
- **`spanFits(prefix, stringCount)`** — префиксная версия `spanReachable`:
  - растяжка прижатых ладов на струнах 1..k не больше `MAX_SPAN = 4`;
  - если лад на струне 0 (большой палец) расширяет растяжку сверх 4 — допустимо только
    когда струн ≥ 4, большой палец не выше остальных и до верхнего лада не больше
    `MAX_SPAN + MAX_THUMB_REACH = 8`.
  - Используется полное число струн `stringCount`, а не длина префикса, т.к. условие
    «≥ 4 струн» в `spanReachable` относится ко всей аппликатуре. **Точное отсечение.**

### 2.2. `strandsMute` + флаг `muteRest` — «висящая» заглушённая струна

`mutesReachable` требует: заглушённая струна между звучащими должна касаться прижатого
(лад > 0) соседа — иначе её нечем заглушить. Раньше это проверялось в конце.

Теперь, когда кладём струну `last`, смотрим на струну `last-1`: если она заглушена,
ниже уже что-то звучит (`playedBefore > 0`), а ни сосед снизу (`last-2`), ни новая
струна (`last`) не прижаты — эта заглушка «висит». Тогда:

- новая струна не может звучать (иначе висящая станет внутренней → неиграбельно);
- если новая струна тоже заглушена — включаем `muteRest = true`: дальше разрешён
  только вариант `null` на всех оставшихся струнах (`muteOnly`).

**Точное отсечение** — отбрасывает только то, что отбросил бы `mutesReachable`.

### 2.3. `MAX_INTERIOR_MUTES = 2` — не больше двух заглушённых струн внутри аккорда

Счётчики `pendingMutes` (заглушки после последней звучащей струны, становятся
«внутренними», как только снова зазвучит струна) и `interiorMutes`. Если вариант
доводит число внутренних заглушек до 3+, он пропускается (`continue`).
Каждая внутренняя заглушка стоит `-30` очков, такие аппликатуры в топ не попадают, но
на длинных строях их комбинаций огромное количество. **Сознательное ограничение**
(меняет множество кандидатов, на видимый результат практически не влияет).

### 2.4. `MAX_SOUNDING_STRINGS = 8` — окно в 8 соседних струн

`firstSounding` — индекс самой низкой звучащей струны. Если текущая струна дальше
неё на 8 и более (`idx - firstSounding >= 8`), разрешён только `null`. До 8 струн
включительно это ничего не меняет; на 9–12 струнах поиск идёт окнами по 8 струн, а
не по всем комбинациям грифа. **Сознательное ограничение** (один удар по струнам не
накрывает больше 8 соседних).

### 2.5. Ограниченный топ вместо полного списка + удаление `seen`/повторного учёта грифов

- Раньше все играбельные аппликатуры складывались в массив и сортировались в конце; на
  длинных строях это миллионы объектов. Теперь держим только лучшие
  `keep = max(RANKED_KEEP = 400, count * 20)`: когда массив дорастает до `2 * keep`,
  сортируем и обрезаем до `keep` (амортизированно дёшево). Т.к. `compareRanked` —
  полный порядок (последний критерий — строка ладов), обрезка не меняет первые `keep`
  мест. `pickDistinct` почти никогда не смотрит дальше нескольких десятков.
- Убран `seen: Set<string>` (на каждого кандидата строилась строка `fretsKey`) — перебор
  никогда не выдаёт одну аппликатуру дважды.
- Убран второй проход `for (grip of gripsForChord()) consider(grip)`: отсечения
  выкидывают только неиграбельное, поэтому каждый играбельный стандартный гриф и так
  выходит из перебора. Грифы нужны только для бонуса `standardGrip` в `score`
  (через `standardGrips`, он остаётся).

### 2.6. Мелочь: массив вместо `Map`

Счётчики нот `counts` — `Int32Array(12)` вместо `Map<number, number>`
(в Kotlin `IntArray(12)`). `distinctChordTones` считает `chordPcs` с `counts[pc] > 0`.

## 3. Готовый код для `src/app/utils/chord-voicing.ts`

Константы рядом с остальными:

```ts
// Muted strings between sounding ones (each costs perInteriorMute); more is never a voicing
// worth showing and only multiplies the search on long tunings.
const MAX_INTERIOR_MUTES = 2;
// Adjacent strings one strum covers. A no-op up to 8-string tunings; on 9-12 strings it keeps
// the search to one 8-string window at a time instead of every combination of the whole neck.
const MAX_SOUNDING_STRINGS = 8;
// How many ranked shapes the search holds at a time (see VoicingSearch.run).
const RANKED_KEEP = 400;
```

Экспортируемые функции (после `analyseFingering`):

```ts
/**
 * Whether the fretting hand can still hold `prefix`, the frets chosen so far on the lowest
 * strings of a `stringCount`-string shape. False means no completion is playable: adding strings
 * only widens the span and adds finger groups, so the search can drop the branch.
 */
export function handFits(prefix: readonly (number | null)[], stringCount: number): boolean {
  return fingerGroups(prefix) <= MAX_FINGERS && spanFits(prefix, stringCount);
}

// Fingers the shape needs at best: one per run of same-fret notes a barre can cover (see
// analyseFingering). Later strings never merge earlier runs, so the count only grows.
function fingerGroups(frets: readonly (number | null)[]): number {
  let groups = 0;
  for (let s = 0; s < frets.length; s++) {
    const fret = frets[s];
    if (fret === null || fret === 0) continue;
    let joins = false;
    for (let p = s - 1; p >= 0; p--) {
      const other = frets[p];
      if (other === null) continue;
      if (other === 0 || other < fret) break;
      if (other === fret) {
        joins = true;
        break;
      }
    }
    if (!joins) groups++;
  }
  return groups;
}

// The partial form of spanReachable: a span past MAX_SPAN can only work with the thumb on the
// lowest string, below every other fretted note and at most MAX_THUMB_REACH under them.
function spanFits(frets: readonly (number | null)[], stringCount: number): boolean {
  let low = Infinity;
  let high = -Infinity;
  for (let s = 1; s < frets.length; s++) {
    const fret = frets[s];
    if (fret === null || fret === 0) continue;
    low = Math.min(low, fret);
    high = Math.max(high, fret);
  }
  const others = low <= high;
  if (others && high - low > MAX_SPAN) return false;
  const thumb = frets[0];
  if (thumb === undefined || thumb === null || thumb <= 0) return true;
  if (!others || Math.max(high, thumb) - Math.min(low, thumb) <= MAX_SPAN) return true;
  if (stringCount < 4 || thumb > low) return false;
  return high - thumb <= MAX_SPAN + MAX_THUMB_REACH;
}
```

`run()` в `VoicingSearch`:

```ts
run(count: number): VoicingShape[] {
  // The enumeration never repeats a shape and its pruning only drops unplayable branches,
  // so every playable standard grip comes out of it too; the grips only add a score bonus.
  // Only the best RANKED_KEEP shapes are kept: on long-scale or 12-string tunings there are
  // millions of playable shapes, and pickDistinct rarely looks past the first few dozen.
  const keep = Math.max(RANKED_KEEP, count * 20);
  const ranked: RankedShape[] = [];
  this.enumerate((frets) => {
    const features = describeShape(frets, this.tuning);
    if (!features || !this.isPlayable(features)) return;
    ranked.push({ features, score: this.score(features) });
    if (ranked.length >= keep * 2) {
      ranked.sort(compareRanked);
      ranked.length = keep;
    }
  });
  ranked.sort(compareRanked);
  return this.pickDistinct(ranked, count).map((entry) => this.toShape(entry));
}
```

`enumerate()`, `strandsMute()` и `distinctChordTones()`:

```ts
private enumerate(visit: (frets: Frets) => void): void {
  const options = this.stringOptions();
  const n = options.length;
  // reachable[s]: pitch classes some string from s upward can still supply.
  const reachable: Set<number>[] = Array.from({ length: n + 1 }, () => new Set<number>());
  for (let s = n - 1; s >= 0; s--) {
    reachable[s] = new Set(reachable[s + 1]);
    for (const fret of options[s]) {
      if (fret !== null) reachable[s].add(mod12(this.tuning.midi[s] + fret));
    }
  }
  const current: (number | null)[] = [];
  const counts = new Int32Array(12);
  const needsDistinct = this.requiredPcs.size >= MIN_DISTINCT;
  const muteOnly: (number | null)[] = [null];

  // muteRest: an earlier muted string has no fretted neighbour, so it may only stay
  // unplayed if every string from here on is muted too (see mutesReachable).
  // pendingMutes: muted strings since the last sounding one, interior once a string sounds.
  // firstSounding: the lowest sounding string so far, -1 before any.
  const walk = (
    idx: number,
    played: number,
    muteRest: boolean,
    pendingMutes: number,
    interiorMutes: number,
    firstSounding: number,
  ): void => {
    for (const pc of this.requiredPcs) if (counts[pc] === 0 && !reachable[idx].has(pc)) return;
    if (played + (n - idx) < MIN_NOTES) return;
    if (needsDistinct && this.distinctChordTones(counts) + (n - idx) < MIN_DISTINCT) return;
    if (idx === n) {
      visit([...current]);
      return;
    }
    const outOfReach = firstSounding >= 0 && idx - firstSounding >= MAX_SOUNDING_STRINGS;
    for (const fret of muteRest || outOfReach ? muteOnly : options[idx]) {
      const interior =
        fret !== null && played > 0 ? interiorMutes + pendingMutes : interiorMutes;
      if (interior > MAX_INTERIOR_MUTES) continue;
      current.push(fret);
      const lock = muteRest || this.strandsMute(current, played);
      if ((fret === null || !lock) && handFits(current, n)) {
        const pc = fret === null ? null : mod12(this.tuning.midi[idx] + fret);
        if (pc !== null) counts[pc]++;
        const pending = fret !== null ? 0 : played > 0 ? pendingMutes + 1 : 0;
        const first = firstSounding < 0 && fret !== null ? idx : firstSounding;
        walk(idx + 1, played + (pc === null ? 0 : 1), lock, pending, interior, first);
        if (pc !== null) counts[pc]--;
      }
      current.pop();
    }
  };
  walk(0, 0, false, 0, 0, -1);
}

// True when the string before the newest one is muted between a sounding string and a
// string that isn't fretted, so nothing may sound after it. `playedBefore` counts the
// sounding strings before the newest one.
private strandsMute(frets: readonly (number | null)[], playedBefore: number): boolean {
  const last = frets.length - 1;
  if (last < 1 || frets[last - 1] !== null) return false;
  if (playedBefore === 0) return false;
  const below = last >= 2 ? frets[last - 2] : null;
  const above = frets[last];
  return !(below !== null && below > 0) && !(above !== null && above > 0);
}

private distinctChordTones(counts: Int32Array): number {
  let distinct = 0;
  for (const pc of this.chordPcs) if (counts[pc] > 0) distinct++;
  return distinct;
}
```

Что **не** трогать: `isPlayable`, `mutesReachable`, `spanReachable`, `analyseFingering`,
`describeShape`, `score`, `WEIGHTS`, `compareRanked`, `pickDistinct`, `searchChord`.
Полные проверки в `isPlayable` остаются как страховка — отсечения их не заменяют.
`gripsForChord()` остаётся для `standardGrips` в конструкторе.

## 4. Тесты (добавить в `chord-voicing.spec.ts`)

Перенесены из `ChordVoicingTest.kt`:

```ts
describe('handFits', () => {
  it('rejects prefixes no hand can hold', () => {
    expect(handFits([1, 3, 3, 2, 1, 1], 6)).toBe(true);
    expect(handFits([5, 5, 5, 5, 5, 5], 6)).toBe(true);
    expect(handFits([3, null, null], 6)).toBe(true);
    expect(handFits([null, 1, 6], 6)).toBe(false); // five-fret span without the thumb
    expect(handFits([1, 2, 3, 4, 5], 6)).toBe(false); // five fingers
    expect(handFits([1, 6], 6)).toBe(true); // thumb below the hand
    expect(handFits([6, 1], 6)).toBe(false); // thumb above the hand
    expect(handFits([1, 10], 6)).toBe(false); // thumb too far below
  });
});

it('searches extended-range tunings', () => {
  const eight = parseTuning('F#1 B1 E2 A2 D3 G3 B3 E4');
  if (!eight.ok) throw new Error('parse failed');
  for (const symbol of ['C', 'Cmaj13', 'C13#11']) {
    expect(searchChord(eight.tuning, chord(symbol)).length).toBeGreaterThan(0);
  }
});
```

Внимание: `Cmaj13` и `C13#11` должны парситься веб-парсером. Если они не парсятся
(в Android в том же коммите были правки парсера, см. раздел 6), оставьте в этом тесте
только `C` или сначала перенесите правки парсера.

Проверка паритета (временная, в коммит не обязательна): скопировать старую
реализацию рядом, прогнать `searchChord(standard, chord(x), 12)` для набора
аккордов (`C, Am, G7, Cmaj7, Dm7b5, Bdim7, E13, A7b13, F#m11, C/E, Am/G, C7alt`)
и сравнить строки ладов. Ожидается совпадение; любое расхождение должно объясняться
лимитом в 2 внутренние заглушки — иначе в переносе ошибка.

Существующие тесты спеки (стандартные грифы, слэш-аккорды, укулеле, 7 струн,
пальцы/баррэ) должны пройти без изменений.

## 5. Работа UI: не блокировать основной поток

В Android поиск выполняется на `Dispatchers.Default` (корутина, которая отменяется при
новом нажатии Generate), а над результатами показывается плавающий индикатор
прогресса. В вебе `generate()` в `src/app/chord-finder/chord-finder.ts` вызывает
`searchChord` синхронно в основном потоке. После оптимизации для 6 струн это ~десятки мс
(незаметно), но для 9–12 струн всё ещё до 1–2 с — интерфейс замрёт.

Рекомендация (не была частью этой оптимизации, отдельный шаг): вынести цикл по
аккордам в Web Worker (в проекте уже есть пример — `pitch-detector.worker`,
подключается через `new Worker(new URL('./…worker', import.meta.url))`), отменять
предыдущий запуск при новом Generate и показывать индикатор, не сдвигающий разметку.
`searchChord` чистая функция без зависимостей от Angular, поэтому переносится в воркер
как есть; результат (`VoicingShape[]`) сериализуем.

## 6. Что ещё было в том же Android-коммите (не оптимизация)

Коммит `5680495` также правил парсер аккордов (`ChordTheory.kt`): заглавная `M` как
мажор (`CM6`, `CM7b5`, `CM9#11`), `min/mi/-` как минор, `7M` как maj7; парсинг `7alt`,
`add13`, `add#11`, `add2/add4/add6`, `madd2`, голых списков альтераций; `aug9/+9/9+5`
сохраняют b7; альтерированные 11/13 заменяют натуральные; мажорные/доминантовые 13
выкидывают натуральную 11. Это отдельная задача — переносить отдельным коммитом, если
нужно. Тест `C7alt → x,3,2,3,4,4` и тест «13-аккорды без натуральной 11» относятся к ней.

## 7. Порядок работы для агента

1. Добавить константы, `handFits`/`fingerGroups`/`spanFits`.
2. Заменить `run`, `enumerate`, `distinctChordTones`; добавить `strandsMute`.
3. Добавить тесты из раздела 4, прогнать всю спеку (vitest).
4. Прогнать проверку паритета на 6 струнах, замерить время на 7, 8 и 12 струнах
   (ориентиры: < 25 мс / < 0,3 с / < 1,6 с; в V8 может отличаться от JVM).
5. Отдельным шагом — Web Worker (раздел 5).
