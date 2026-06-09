/**
 * 한국 공휴일·기념일 카탈로그.
 *  - 공휴일(설/추석/부처님오신날/대체공휴일 포함)은 백엔드 `/api/holidays?year=YYYY` 에서 받아온다.
 *    백엔드는 공공데이터포털 특일정보 API 결과를 연 단위로 캐시.
 *  - 인증키 미설정 등으로 백엔드가 빈 리스트를 주면 클라이언트의 `korean-lunar-calendar` fallback 으로
 *    음력 명절 + 양력 고정 공휴일을 직접 계산해 채운다.
 *  - 기념일(어버이날/스승의 날/5·18)은 양력 고정이라 매번 클라이언트에서 추가한다.
 *
 *  공개 API:
 *    `ensureHolidaysForYear(year)`  연도별 휴일을 lazy fetch. 새로 로드되면 true 반환 (재렌더 신호).
 *    `getHolidaysForDate(date)`     특정 날짜의 휴일 항목 배열 반환 (월 셀 인라인 라벨용)
 *  반환 항목: { title, shortTitle, kind }
 *    - kind: "PUBLIC"(법정 공휴일·빨간 글자) | "OBSERVANCE"(기념일·호박색 글자)
 *    - shortTitle: 한 줄 표시용 축약 (예: "부처님오신날 대체공휴일" → "대체공휴일")
 */
import KoreanLunarCalendar from "korean-lunar-calendar";

const HOLIDAY_API = "/api/holidays";

/** 양력 고정 공휴일 — fallback 전용. substitute 는 대체공휴일 적용 방식. */
const SOLAR_PUBLIC = [
  { month: 1,  day: 1,  title: "신정",       substitute: "none"        },
  { month: 3,  day: 1,  title: "삼일절",     substitute: "sunday"      },
  { month: 5,  day: 5,  title: "어린이날",   substitute: "weekend"     },
  { month: 6,  day: 6,  title: "현충일",     substitute: "none"        },
  { month: 8,  day: 15, title: "광복절",     substitute: "sunday"      },
  { month: 10, day: 3,  title: "개천절",     substitute: "sunday"      },
  { month: 10, day: 9,  title: "한글날",     substitute: "sunday"      },
  { month: 12, day: 25, title: "성탄절",     substitute: "sunday"      },
];

/** 기념일(빨간 날 아님) — 항상 클라이언트에서 추가. */
const SOLAR_OBSERVANCES = [
  { month: 5, day: 8,  title: "어버이날",                 shortTitle: "어버이날"   },
  { month: 5, day: 15, title: "스승의 날",                shortTitle: "스승의 날"  },
  { month: 5, day: 18, title: "5·18 민주화운동 기념일",   shortTitle: "5·18 기념일" },
];

/** 연도별 캐시 — Map<year, Map<"YYYY-MM-DD", Array<HolidayEntry>>> */
const yearCache = new Map();
/** 동일 연도 동시 호출 시 fetch 중복을 막기 위한 in-flight Promise 캐시. */
const inflight = new Map();

/**
 * 연도별 휴일을 lazy 로 채운다.
 *  - 캐시 hit 시 즉시 false 반환 (재렌더 불필요).
 *  - 새로 로드되면 true 반환 → 호출자가 calendar.render() 로 셀을 갱신해야 라벨이 보인다.
 * @param {number} year
 * @returns {Promise<boolean>} 새로 로드되었는지 여부
 */
export async function ensureHolidaysForYear(year) {
  if (yearCache.has(year)) return false;
  if (inflight.has(year)) {
    await inflight.get(year);
    return true;
  }

  const job = (async () => {
    const map = new Map();
    let apiItems = [];
    try {
      apiItems = await fetchHolidaysFromBackend(year);
    } catch {
      apiItems = [];
    }

    if (apiItems.length > 0) {
      installFromApi(map, apiItems);
    } else {
      installFromLunarFallback(map, year);
    }
    // 기념일은 어느 경로든 클라이언트에서 추가.
    installObservances(map, year);

    yearCache.set(year, map);
  })();

  inflight.set(year, job);
  try {
    await job;
  } finally {
    inflight.delete(year);
  }
  return true;
}

/**
 * 특정 날짜의 휴일 항목들. 미리 ensureHolidaysForYear() 가 호출되어 있어야 한다.
 * @param {Date} date
 * @returns {Array<{ title: string, shortTitle: string, kind: "PUBLIC"|"OBSERVANCE" }>}
 */
export function getHolidaysForDate(date) {
  if (!(date instanceof Date) || Number.isNaN(date.getTime())) return [];
  const yearMap = yearCache.get(date.getFullYear());
  if (!yearMap) return [];
  return yearMap.get(formatIsoDate(date)) ?? [];
}

// ---------------------------------------------------------------------------
// 백엔드 fetch
// ---------------------------------------------------------------------------

async function fetchHolidaysFromBackend(year) {
  const res = await fetch(`${HOLIDAY_API}?year=${year}`, {
    method: "GET",
    headers: { Accept: "application/json" },
    credentials: "same-origin",
  });
  if (!res.ok) return [];
  const data = await res.json();
  return Array.isArray(data) ? data : [];
}

/**
 * 백엔드 응답({ date, name, kind }) 을 캐시 Map 에 설치.
 *  - 같은 날짜에 다른 이름이 또 오면 후행을 추가(공휴일 겹침 케이스 — 드물지만 가능).
 */
function installFromApi(map, items) {
  items.forEach((row) => {
    if (!row || typeof row.date !== "string" || typeof row.name !== "string") return;
    const kind = row.kind === "OBSERVANCE" ? "OBSERVANCE" : "PUBLIC";
    pushEntry(map, row.date, row.name, shortenTitle(row.name), kind);
  });
}

/** 응답이 비었을 때(인증키 미설정 등) 자체 lunar 계산으로 채우는 fallback. */
function installFromLunarFallback(map, year) {
  // 1) 양력 고정 공휴일 + 대체공휴일
  SOLAR_PUBLIC.forEach((entry) => {
    const base = new Date(year, entry.month - 1, entry.day);
    pushEntry(map, formatIsoDate(base), entry.title, entry.title, "PUBLIC");
    const subDate = computeSubstituteDate(base, entry.substitute);
    if (subDate) pushEntry(map, formatIsoDate(subDate), `${entry.title} 대체공휴일`, "대체공휴일", "PUBLIC");
  });

  // 2) 음력 공휴일 — 설날/추석(연휴 3일) + 부처님오신날
  const seollal = lunarToSolar(year, 1, 1);
  if (seollal) {
    pushEntry(map, formatIsoDate(addDays(seollal, -1)), "설날 연휴", "설 연휴", "PUBLIC");
    pushEntry(map, formatIsoDate(seollal),              "설날",     "설날",    "PUBLIC");
    pushEntry(map, formatIsoDate(addDays(seollal, 1)),  "설날 연휴", "설 연휴", "PUBLIC");
    const seollalSub = computeLunarHolidaySubstitute([addDays(seollal, -1), seollal, addDays(seollal, 1)]);
    if (seollalSub) pushEntry(map, formatIsoDate(seollalSub), "설날 대체공휴일", "대체공휴일", "PUBLIC");
  }

  const chuseok = lunarToSolar(year, 8, 15);
  if (chuseok) {
    pushEntry(map, formatIsoDate(addDays(chuseok, -1)), "추석 연휴", "추석 연휴", "PUBLIC");
    pushEntry(map, formatIsoDate(chuseok),              "추석",     "추석",     "PUBLIC");
    pushEntry(map, formatIsoDate(addDays(chuseok, 1)),  "추석 연휴", "추석 연휴", "PUBLIC");
    const chuseokSub = computeLunarHolidaySubstitute([addDays(chuseok, -1), chuseok, addDays(chuseok, 1)]);
    if (chuseokSub) pushEntry(map, formatIsoDate(chuseokSub), "추석 대체공휴일", "대체공휴일", "PUBLIC");
  }

  const buddhaBirth = lunarToSolar(year, 4, 8);
  if (buddhaBirth) {
    pushEntry(map, formatIsoDate(buddhaBirth), "부처님오신날", "부처님오신날", "PUBLIC");
    const buddhaSub = computeSubstituteDate(buddhaBirth, "sunday");
    if (buddhaSub) pushEntry(map, formatIsoDate(buddhaSub), "부처님오신날 대체공휴일", "대체공휴일", "PUBLIC");
  }
}

/** 기념일(어버이날·스승의 날·5·18)은 어느 경로든 클라이언트에서 추가. */
function installObservances(map, year) {
  SOLAR_OBSERVANCES.forEach((entry) => {
    const iso = formatIsoDate(new Date(year, entry.month - 1, entry.day));
    pushEntry(map, iso, entry.title, entry.shortTitle, "OBSERVANCE");
  });
}

function pushEntry(map, iso, title, shortTitle, kind) {
  if (!map.has(iso)) map.set(iso, []);
  map.get(iso).push({ title, shortTitle, kind });
}

/**
 * 백엔드가 알려준 풀네임을 셀 라벨에 어울리게 줄인다.
 *  - "X 대체공휴일"            → "대체공휴일"
 *  - "5·18 민주화운동 기념일"  → "5·18 기념일"  (현재 백엔드 PUBLIC 응답에는 안 옴, 안전망)
 *  - "설날" 앞뒤 1일을 받는 "설날 연휴" 표기는 백엔드가 자체적으로 줄여서 옴
 *    (보통 "설날", "추석" 만 옴 → 굳이 줄이지 않음)
 *  - 그 외는 원본 유지.
 */
function shortenTitle(name) {
  const s = String(name || "").trim();
  if (s.endsWith("대체공휴일") && s !== "대체공휴일") return "대체공휴일";
  if (s === "5·18민주화운동기념일" || s === "5·18 민주화운동 기념일") return "5·18 기념일";
  return s;
}

// ---------------------------------------------------------------------------
// lunar/대체공휴일 fallback 유틸 (인증키 없을 때만 사용)
// ---------------------------------------------------------------------------

function lunarToSolar(year, lunarMonth, lunarDay) {
  try {
    const calendar = new KoreanLunarCalendar();
    const ok = calendar.setLunarDate(year, lunarMonth, lunarDay, false);
    if (!ok) return null;
    const solar = calendar.getSolarCalendar();
    if (!solar || typeof solar.year !== "number") return null;
    return new Date(solar.year, solar.month - 1, solar.day);
  } catch {
    return null;
  }
}

function computeSubstituteDate(base, mode) {
  const day = base.getDay();
  if (mode === "weekend") {
    if (day === 6) return addDays(base, 2);
    if (day === 0) return addDays(base, 1);
    return null;
  }
  if (mode === "sunday") {
    if (day === 0) return addDays(base, 1);
    return null;
  }
  return null;
}

function computeLunarHolidaySubstitute(holidayDates) {
  const hasSunday = holidayDates.some((d) => d.getDay() === 0);
  if (!hasSunday) return null;
  const last = holidayDates[holidayDates.length - 1];
  let candidate = addDays(last, 1);
  while (candidate.getDay() === 0 || candidate.getDay() === 6) {
    candidate = addDays(candidate, 1);
  }
  return candidate;
}

function addDays(date, days) {
  const d = new Date(date.getTime());
  d.setDate(d.getDate() + days);
  return d;
}

function formatIsoDate(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}
