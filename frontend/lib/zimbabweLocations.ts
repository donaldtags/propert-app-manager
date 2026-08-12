export const ZIMBABWE_CITIES: string[] = [
  "Harare",
  "Bulawayo",
  "Chitungwiza",
  "Mutare",
  "Gweru",
  "Epworth",
  "Kwekwe",
  "Kadoma",
  "Masvingo",
  "Chinhoyi",
  "Norton",
  "Marondera",
  "Ruwa",
  "Chegutu",
  "Bindura",
  "Beitbridge",
  "Redcliff",
  "Victoria Falls",
  "Hwange",
  "Zvishavane",
  "Kariba",
  "Chiredzi",
  "Karoi",
  "Rusape",
  "Chipinge",
  "Gwanda",
  "Shurugwi",
  "Plumtree",
];

export const ZIMBABWE_SUBURBS: Record<string, string[]> = {
  Harare: [
    "Borrowdale", "Borrowdale Brooke", "Avondale", "Mount Pleasant", "Highlands", "Greendale",
    "Hatfield", "Mabelreign", "Strathaven", "Msasa", "Dzivarasekwa", "Belvedere", "Waterfalls",
    "Marlborough", "Eastlea", "Milton Park", "Alexandra Park", "Chisipite", "Vainona",
    "Glen Lorne", "Kambanji", "Kuwadzana", "Budiriro", "Warren Park", "Mbare", "Southerton",
    "Belgravia", "Ashdown Park", "Newlands", "Emerald Hill", "Gunhill", "Colne Valley",
    "Gletwyn", "Helensvale", "Ballantyne Park", "Chadcombe", "Hillside (Harare)", "Mount Hampden",
    "Sunningdale", "Cranborne", "Braeside", "Arcadia", "Prospect", "Waterfalls South",
    "Kambuzuma", "Glen View", "Glen Norah", "Highfield", "Rugare", "Tafara", "Mabvuku",
    "Greendale North", "Chizhanje", "Zimre Park", "Ashbrittle", "Philadelphia", "Malbereign",
    "Meyrick Park", "Pomona", "Umwinsidale", "Ridgeview", "Adylin", "Westgate",
  ],
  Bulawayo: [
    "Suburbs", "Hillside", "Burnside", "Selborne Park", "Matsheumhlope", "North End",
    "Fourwinds", "Kumalo", "Barham Green", "Riverside", "Famona", "Ilanda", "Woodville",
    "Northlynne", "Paddonhurst", "Morningside", "Killarney", "Belmont", "Kelvin",
    "Waterford", "Emakhandeni", "Nkulumane", "Pumula", "Luveve", "Entumbane", "Cowdray Park",
  ],
  Chitungwiza: ["St Mary's", "Zengeza", "Seke", "Unit L", "Manyame Park"],
  Mutare: ["Greenside", "Palmerstone", "Morningside (Mutare)", "Fern Valley", "Yeovil", "Chikanga", "Dangamvura"],
  Gweru: ["Ridgemont", "Nehosho", "Mkoba", "Windsor Park", "Athlone", "Senga"],
  Epworth: ["Overspill", "Zinyengere", "Chinamano"],
  Kwekwe: ["Amaveni", "Mbizo", "Rutendo", "Redcliff Extension"],
  Kadoma: ["Rimuka", "Ngezi", "Waverley"],
  Masvingo: ["Rujeko", "Mucheke", "Runyararo"],
  Chinhoyi: ["Cold Comfort", "Chikonohono", "Brooksdale"],
  Norton: ["Katanga", "Simuchembu", "Westview"],
  Marondera: ["Dombotombo", "Cherutombo", "Rujeko (Marondera)"],
  Ruwa: ["Damofalls", "Ruwa Central", "Grasslands"],
  Bindura: ["Chipadze", "Aerodrome"],
  "Victoria Falls": ["Chinotimba", "Mkhosana", "Central"],
};

export function suburbsForCity(city: string): string[] {
  return ZIMBABWE_SUBURBS[city] ?? [];
}

/** Every "suburb, city" pair, flattened, for search/autocomplete across all cities. */
export const ZIMBABWE_SUBURB_ENTRIES: { suburb: string; city: string }[] = Object.entries(
  ZIMBABWE_SUBURBS
).flatMap(([city, suburbs]) => suburbs.map((suburb) => ({ suburb, city })));

/**
 * Resolves free-text location input to a known city, and/or a known suburb + its city.
 * Cities are matched case-insensitively (exact); suburbs are matched case-insensitively
 * (exact first, then "starts with") since city filtering on the backend is an exact match
 * but suburb filtering is a partial match.
 */
export function resolveLocationQuery(
  query: string
): { city: string; suburb: string } | null {
  const term = query.trim().toLowerCase();
  if (!term) return null;

  const cityMatch = ZIMBABWE_CITIES.find((c) => c.toLowerCase() === term);
  if (cityMatch) return { city: cityMatch, suburb: "" };

  const exactSuburb = ZIMBABWE_SUBURB_ENTRIES.find((e) => e.suburb.toLowerCase() === term);
  if (exactSuburb) return { city: exactSuburb.city, suburb: exactSuburb.suburb };

  const partialSuburb = ZIMBABWE_SUBURB_ENTRIES.find((e) => e.suburb.toLowerCase().startsWith(term));
  if (partialSuburb) return { city: partialSuburb.city, suburb: partialSuburb.suburb };

  return null;
}