"""Genera docs/db/seed-demo.sql: 6 meses de operación simulada de 10 camiones, más 2 utilitarios, 7 remolques
y un vehículo sin tipo del catálogo (CAM-157).
Uso: python docs/db/generate-seed-demo.py (misma semilla -> mismo SQL).

Todas las fechas se emiten relativas a current_date (día 0 = el día que se corre el script),
así los estados al_dia / por_vencer / vencido se mantienen aunque se corra meses después.
"""
import os
import random
import uuid

random.seed(20260927)
NS = uuid.UUID("6f1c3a52-2b1e-4c55-9a0e-5d8f3f0a9c11")
HISTORY_START = -182
PHOTO_BASE = "http://localhost:8080/api/photos/"
WARN_KM, WARN_DAYS = 1000, 7

_counter = 0


def uid(kind):
    global _counter
    _counter += 1
    return str(uuid.uuid5(NS, f"{kind}-{_counter}"))


def q(s):
    return "null" if s is None else "'" + str(s).replace("'", "''") + "'"


def ts(day, hh, mm=0):
    return f"pg_temp.ts({day},'{hh:02d}:{mm:02d}')"


def dt(day):
    return f"pg_temp.d({day})"


def photo(name):
    return PHOTO_BASE + name


# ---------------------------------------------------------------- usuarios
H_CHOFER = "$2a$10$edcMcLxUUrmfxqfR3hWYvOC66svPwpGRgoEhM/i.l8WwX9ZOxo1Hi"   # chofer123
H_TECNICO = "$2a$10$uGDj/epb3fSJk1vgvVGqr.UhYwJtvrB3Tmkom8dAdTNMQnrr90jhG"  # tecnico123
H_ADMIN = "$2a$10$f.6fLuq50cD/q6x3p9Gcx.LKvuE/XCL11PDvrq9IFQTjdK15vfHKi"    # admin123

DRIVERS = {
    "carlos.gomez": "Carlos Gómez",
    "lucas.fernandez": "Lucas Fernández",
    "martin.rodriguez": "Martín Rodríguez",
    "diego.sosa": "Diego Sosa",
    "pablo.acosta": "Pablo Acosta",
}
TECHS = {"javier.romero": uid("user"), "nicolas.benitez": uid("user")}
DRIVER_IDS = {u: uid("user") for u in DRIVERS}

# ---------------------------------------------------------------- vehículos
# clase: larga (tractor larga distancia), regional, urbano
VEHICLES = [
    dict(key="scania", plate="AE215KT", brand="Scania", model="R 450 A4x2", year=2021, cls="larga", km=412_300, vin="9BSR4X200M3981245", driver="carlos.gomez"),
    dict(key="volvo", plate="AF638RB", brand="Volvo", model="FH 460 4x2", year=2022, cls="larga", km=297_800, vin="9BVAG20C2NE872310", driver="carlos.gomez"),
    dict(key="actros", plate="AD104PW", brand="Mercedes-Benz", model="Actros 2651 LS 6x4", year=2019, cls="larga", km=624_900, vin="9BM958074KB145872", driver="lucas.fernandez"),
    dict(key="sway", plate="AG312LN", brand="Iveco", model="S-Way 480 4x2", year=2023, cls="larga", km=158_600, vin="93ZS2RML0P8871236", driver="lucas.fernandez"),
    dict(key="constellation", plate="AE487GD", brand="Volkswagen", model="Constellation 17.280", year=2020, cls="regional", km=266_400, vin="9536G8241LR031772", driver="martin.rodriguez"),
    dict(key="atego", plate="AC921FS", brand="Mercedes-Benz", model="Atego 1726", year=2018, cls="regional", km=341_200, vin="9BM958171JB098341", driver="martin.rodriguez"),
    dict(key="tector", plate="AD556HX", brand="Iveco", model="Tector 240E28 6x2", year=2019, cls="regional", km=289_700, vin="93ZA1RHH0K8843092", driver="diego.sosa"),
    dict(key="cargo", plate="AB733MC", brand="Ford", model="Cargo 1723", year=2017, cls="regional", km=386_100, vin="9BFYEAHU3HBS62871", driver="diego.sosa"),
    dict(key="delivery", plate="AF102ZT", brand="Volkswagen", model="Delivery 11.180", year=2022, cls="urbano", km=97_600, vin="9532M52P2NR204517", driver="pablo.acosta"),
    dict(key="accelo", plate="AA846JQ", brand="Mercedes-Benz", model="Accelo 1016", year=2016, cls="urbano", km=309_400, vin="9BM979076GB012384", driver="pablo.acosta"),
]
for v in VEHICLES:
    v["id"] = uid("vehicle")
VBY = {v["key"]: v for v in VEHICLES}

KM_PER_DAY = {"larga": (560, 760), "regional": (240, 430), "urbano": (110, 210)}
TRIP_DAYS = {"larga": (2, 4), "regional": (1, 2), "urbano": (1, 1)}

ROUTES = {
    "larga": ["Buenos Aires - Córdoba", "Buenos Aires - Mendoza", "Rosario - Tucumán", "Buenos Aires - Neuquén",
              "Buenos Aires - Bahía Blanca", "Zárate - Salta", "Buenos Aires - Posadas"],
    "regional": ["Reparto Rosario", "Buenos Aires - Mar del Plata", "Reparto zona norte GBA", "Buenos Aires - La Plata",
                 "Pilar - Luján - Mercedes", "Buenos Aires - San Nicolás", "Reparto zona oeste GBA"],
    "urbano": ["Reparto CABA centro", "Reparto zona sur", "Reparto CABA norte", "Reparto Avellaneda - Quilmes"],
}

# Escenarios del día 0 (ver resumen al final del archivo SQL)
NO_TRIP_FROM = {"constellation": -1, "atego": -1}  # estacionados: sin viajes que arranquen desde este día

# ---------------------------------------------------------------- simulación de viajes
trips = []  # dict(vehicle, driver, start, end, km_start, km_end, open)
odo = {v["key"]: 0.0 for v in VEHICLES}
busy_until = {v["key"]: HISTORY_START - 1 for v in VEHICLES}   # último día ocupado
unavailable_until = {v["key"]: HISTORY_START - 1 for v in VEHICLES}
driver_free = {d: HISTORY_START for d in DRIVERS}
pairs = {}
for v in VEHICLES:
    pairs.setdefault(v["driver"], []).append(v["key"])

random_defect_events = []  # (trip index, kind) decididos durante la simulación

for day in range(HISTORY_START, 0):
    for drv, keys in pairs.items():
        if driver_free[drv] > day:
            continue
        long_haul_now = day >= -3 and VBY[keys[0]]["cls"] == "larga"
        if random.random() < 0.22 and not long_haul_now:  # franco / día de base
            continue
        options = [k for k in keys if busy_until[k] < day and unavailable_until[k] < day
                   and day < NO_TRIP_FROM.get(k, 1)]
        if not options:
            continue
        # alterna: prefiere el camión que hace más que no sale
        options.sort(key=lambda k: busy_until[k])
        k = options[0]
        cls = VBY[k]["cls"]
        length = random.randint(*TRIP_DAYS[cls])
        if cls == "larga" and day >= -3:
            length = max(length, -day + 2)  # los de larga distancia quedan en ruta hoy
        end = day + length - 1
        km = sum(random.randint(*KM_PER_DAY[cls]) for _ in range(length))
        is_open = end >= 0
        t = dict(vehicle=k, driver=drv, start=day, end=end, km_start=odo[k], km_end=odo[k] + km, open=is_open,
                 route=random.choice(ROUTES[cls]))
        trips.append(t)
        if is_open:
            # el viaje sigue en curso: el odómetro queda en el km de salida
            busy_until[k] = 10 ** 6
            driver_free[drv] = 10 ** 6
        else:
            odo[k] += km
            busy_until[k] = end
            driver_free[drv] = end + (2 if cls == "larga" else 1)
            # defectos al azar en el historial (hasta 20 días atrás, los recientes son guionados)
            if end < -20 and random.random() < 0.095:
                blocking = random.random() < 0.18
                random_defect_events.append((len(trips) - 1, blocking))
                if blocking:
                    unavailable_until[k] = end + random.randint(1, 3)
                    driver_free[drv] = min(driver_free[drv], end + 1)

# normaliza km para que el odómetro final coincida con el del vehículo
for v in VEHICLES:
    k = v["key"]
    offset = v["km"] - odo[k]
    for t in trips:
        if t["vehicle"] == k:
            t["km_start"] = int(round(t["km_start"] + offset))
            t["km_end"] = int(round(t["km_end"] + offset))
    v["odometer"] = v["km"]

# línea de tiempo por vehículo: km al final de cada día y días libres (sin viaje)
timeline = {}
for v in VEHICLES:
    k = v["key"]
    vt = sorted([t for t in trips if t["vehicle"] == k], key=lambda t: t["start"])
    km_day, idle = {}, {}
    first_km = vt[0]["km_start"] if vt else v["km"]
    total_closed = sum(t["km_end"] - t["km_start"] for t in vt if not t["open"])
    v["rate"] = max(total_closed / -HISTORY_START, 50)
    cur = first_km
    for day in range(HISTORY_START, 1):
        on_trip = any(t["start"] <= day <= t["end"] or (t["open"] and t["start"] <= day) for t in vt)
        for t in vt:
            if not t["open"] and t["end"] == day:
                cur = t["km_end"]
        km_day[day] = cur
        idle[day] = not on_trip
    v["first_km"] = first_km
    timeline[k] = (km_day, idle)


def km_at(k, day):
    km_day, _ = timeline[k]
    if day >= HISTORY_START:
        return km_day[min(day, 0)]
    v = VBY[k]
    return max(0, int(v["first_km"] + v["rate"] * (day - HISTORY_START)))


def is_idle(k, day):
    if day < HISTORY_START:
        return True
    return timeline[k][1][day]


# ---------------------------------------------------------------- planes
PLANS = [
    dict(key="aceite", name="Aceite y filtros de motor", category="motor", type="BOTH", km=15000, days=180),
    dict(key="aceite_ld", name="Aceite motor larga distancia", category="motor", type="BOTH", km=40000, days=365),
    dict(key="aire", name="Cambio de filtro de aire", category="motor", type="KM", km=40000, days=None),
    dict(key="combustible", name="Filtros de combustible", category="motor", type="KM", km=30000, days=None),
    dict(key="frenos", name="Revisión de frenos", category="frenos", type="BOTH", km=30000, days=180),
    dict(key="rotacion", name="Rotación de neumáticos", category="neumáticos", type="KM", km=20000, days=None),
    dict(key="alineacion", name="Alineación y balanceo", category="neumáticos", type="KM", km=50000, days=None),
    dict(key="engrase", name="Engrase general de chasis", category="chasis", type="BOTH", km=10000, days=30),
    dict(key="refrigerante", name="Cambio de líquido refrigerante", category="motor", type="TIME", km=None, days=730),
    dict(key="rto", name="Revisión técnica (RTO)", category="documentación", type="TIME", km=None, days=365, doc="VTV"),
    dict(key="bateria", name="Batería y sistema eléctrico", category="eléctrico", type="TIME", km=None, days=90),
    dict(key="caja", name="Aceite de caja y diferencial", category="transmisión", type="BOTH", km=120000, days=730),
    dict(key="matafuegos", name="Recarga de matafuegos", category="seguridad", type="TIME", km=None, days=365),
    dict(key="aa", name="Gas de aire acondicionado", category="confort", type="TIME", km=None, days=365, active=False),
]
for p in PLANS:
    p["id"] = uid("plan")
PBY = {p["key"]: p for p in PLANS}


def plans_for(v):
    base = ["aire", "combustible", "frenos", "rotacion", "alineacion", "engrase", "refrigerante", "rto", "bateria",
            "caja", "matafuegos"]
    return (["aceite_ld"] if v["cls"] == "larga" else ["aceite"]) + base


def status_of(next_km, next_date, cur_km):
    if (next_km is not None and cur_km > next_km) or (next_date is not None and 0 > next_date):
        return "vencido"
    if (next_km is not None and next_km - cur_km <= WARN_KM) or (next_date is not None and next_date <= WARN_DAYS):
        return "por_vencer"
    return "al_dia"


def next_due(p, km, day):
    return (km + p["km"] if p["km"] else None, day + p["days"] if p["days"] else None)


def usage(p, km, day, cur_km, cur_day):
    r = 0
    if p["km"]:
        r = max(r, (cur_km - km) / p["km"])
    if p["days"]:
        r = max(r, (cur_day - day) / p["days"])
    return r


# Estado objetivo por (vehículo, plan). Lo no listado queda al_dia.
TARGET = {
    ("actros", "aceite_ld"): "vencido",
    ("cargo", "rto"): "vencido",
    ("cargo", "engrase"): "por_vencer",
    ("volvo", "engrase"): "por_vencer",
    ("atego", "frenos"): "por_vencer",
    ("accelo", "bateria"): "por_vencer",
}

assignments = []  # dict(id, vehicle, plan, last_km, last_day, completions=[(day, km)])
workshop_days = {v["key"]: set() for v in VEHICLES}

for v in VEHICLES:
    k = v["key"]
    cur_km = v["odometer"]
    for pk in plans_for(v):
        p = PBY[pk]
        target = TARGET.get((k, pk), "al_dia")
        span = (p["days"] or 0) + 1
        if p["km"]:
            span = max(span, int(p["km"] / v["rate"] * 1.6) + 30)
        candidates = []
        min_day = -(2026 - v["year"]) * 365 - 90
        for day in range(max(-span - 60, min_day), 0):
            if not is_idle(k, day):
                continue
            km = km_at(k, day)
            nk, nd = next_due(p, km, day)
            if status_of(nk, nd, cur_km) != target:
                continue
            u = usage(p, km, day, cur_km, 0)
            candidates.append((day, km, u))
        assert candidates, (k, pk, target)
        if target == "al_dia":
            pref = random.uniform(0.2, 0.8)
        elif target == "por_vencer":
            pref = 0.97
        else:
            pref = 1.08
        candidates.sort(key=lambda c: abs(c[2] - pref) - (0.04 if c[0] in workshop_days[k] else 0))
        last_day, last_km, _ = candidates[0]
        comps = [(last_day, last_km)]
        # hacia atrás: cada mantenimiento previo se hizo con el 85-99 % del intervalo consumido
        d = last_day
        while True:
            want = random.uniform(0.86, 0.99)
            best = None
            for prev in range(d - 1, d - 800, -1):
                if not is_idle(k, prev):
                    continue
                u = usage(p, km_at(k, prev), prev, km_at(k, d), d)
                if u > 1.0:
                    break
                score = abs(u - want) - (0.03 if prev in workshop_days[k] else 0)
                if best is None or score < best[0]:
                    best = (score, prev)
            if best is None or best[1] < HISTORY_START:
                break
            d = best[1]
            comps.append((d, km_at(k, d)))
        comps.reverse()
        in_window = [c for c in comps if c[0] >= HISTORY_START]
        for c in in_window:
            workshop_days[k].add(c[0])
        a = dict(id=uid("assignment"), vehicle=k, plan=pk, last_day=last_day, last_km=last_km, completions=in_window)
        nk, nd = next_due(p, last_km, last_day)
        a["next_km"], a["next_day"] = nk, nd
        a["status"] = status_of(nk, nd, cur_km)
        assert a["status"] == target
        assignments.append(a)

# CAM-155: en una flota real algunos mantenimientos se hacen después de vencer. A cerca de un tercio de los
# intermedios (ni el primero, que no tiene con qué compararse, ni el último, que define el estado de hoy) se
# los corre a un día con el 104-120 % del intervalo consumido. Generador aparte: el resto del seed no cambia.
late_rng = random.Random(20261106)
late_count = 0
for a in assignments:
    k, p, comps = a["vehicle"], PBY[a["plan"]], a["completions"]
    for i in range(1, len(comps) - 1):
        if late_rng.random() >= 0.35:
            continue
        prev_day, prev_km = comps[i - 1]
        options = [d for d in range(comps[i][0] + 1, comps[i + 1][0] - 7)
                   if is_idle(k, d) and 1.04 <= usage(p, prev_km, prev_day, km_at(k, d), d) <= 1.2]
        if options:
            d = late_rng.choice(options)
            comps[i] = (d, km_at(k, d))
            late_count += 1

ABY = {(a["vehicle"], a["plan"]): a for a in assignments}

# ---------------------------------------------------------------- inspecciones y defectos
PRE_ITEMS = ["ext-luces", "ext-neumaticos", "ext-carroceria", "ext-fugas", "int-km", "int-documentacion", "int-testigos"]
POST_ITEMS = ["post-danos", "post-luces", "post-fugas", "post-km"]

# (item, severidad, descripción, foto del defecto, tipo de reparación)
DEFECT_POOL_POST_BLOCKING = [
    ("post-fugas", "BLOCKING", "Pérdida de aire en frenos", "demo-fuga-aire-frenos", "aire"),
    ("post-fugas", "BLOCKING", "Pérdida de aceite del motor", "demo-fuga-aceite", "aceite"),
    ("post-luces", "BLOCKING", "No encienden luces de freno", "demo-luz-trasera-rota", "luces"),
    ("post-danos", "BLOCKING", "Espejo izquierdo arrancado", "demo-espejo-roto", "espejo"),
]
DEFECT_POOL_NONBLOCKING = [
    ("ext-neumaticos", "Desgaste irregular neumático", "demo-neumatico-gastado", "neumatico"),
    ("ext-neumaticos", "Corte en flanco de neumático", "demo-neumatico-corte", "neumatico"),
    ("ext-luces", "Óptica delantera derecha rota", "demo-faro-roto", "optica"),
    ("ext-luces", "Farol trasero con tapa partida", "demo-luz-trasera-rota", "luces"),
    ("ext-carroceria", "Parabrisas rajado", "demo-parabrisas-rajado", "parabrisas"),
    ("ext-carroceria", "Espejo derecho rajado", "demo-espejo-roto", "espejo"),
    ("ext-carroceria", "Abolladura lateral de la caja", "demo-carroceria-abollada", "chapa"),
    ("ext-fugas", "Goteo de aceite bajo el motor", "demo-fuga-aceite", "aceite"),
    ("int-testigos", "Testigo de ABS intermitente", "demo-testigo-tablero", "abs"),
    ("int-testigos", "Testigo de batería encendido", "demo-testigo-tablero", "bateria_def"),
    ("int-documentacion", "Falta tarjeta del seguro", None, "doc"),
    ("post-danos", "Raspón en paragolpes trasero", "demo-carroceria-abollada", "chapa"),
    ("post-luces", "Luz de posición quemada", None, "luces"),
]

inspections, answers, defects = [], [], []


def add_inspection(t, kind):
    k = t["vehicle"]
    is_pre = kind == "PRE_TRIP"
    day = t["start"] if is_pre else t["end"]
    hh, mm = (random.randint(5, 7), random.randint(0, 59)) if is_pre else (random.randint(17, 20), random.randint(0, 59))
    notes = None
    if is_pre and random.random() < 0.18:
        notes = random.choice([f"Salida {t['route']}", f"Carga completa, {t['route']}", "Salgo con media carga",
                               f"{t['route']}, vuelvo con retorno", "Cargué gasoil en base antes de salir"])
    elif not is_pre and random.random() < 0.12:
        notes = random.choice(["Mucho viento en ruta, sin novedades", "Demora en descarga de 3 horas",
                               "Ruta con piquete, se desvió por colectora", "Lluvia fuerte en el último tramo",
                               "Sin novedades"])
    ins = dict(id=uid("inspection"), trip=t["id"], vehicle=k, driver=t["driver"], type=kind, day=day, hh=hh, mm=mm,
               km=t["km_start"] if is_pre else t["km_end"], notes=notes, blocking=False, answers=[])
    for item in (PRE_ITEMS if is_pre else POST_ITEMS):
        a = dict(id=uid("answer"), inspection=ins["id"], item=item, outcome=None, number=None)
        if item in ("int-km", "post-km"):
            a["number"] = ins["km"]
        else:
            a["outcome"] = "OK"
        ins["answers"].append(a)
    inspections.append(ins)
    t.setdefault("inspections", {})[kind] = ins
    return ins


def add_defect(ins, item, severity, description, photo_name, repair, status="open", with_photo=None):
    ans = next(a for a in ins["answers"] if a["item"] == item)
    ans["outcome"] = "DEFECT"
    if severity == "BLOCKING":
        ins["blocking"] = True
        with_photo = True
    if with_photo is None:
        with_photo = random.random() < 0.65
    d = dict(id=uid("defect"), answer=ans["id"], vehicle=ins["vehicle"], severity=severity, description=description,
             photo=photo(photo_name) if (with_photo and photo_name) else None, day=ins["day"],
             hh=ins["hh"], mm=ins["mm"] + 5 if ins["mm"] < 54 else ins["mm"], status=status, repair=repair)
    defects.append(d)
    return d


for t in trips:
    t["id"] = uid("trip")
    add_inspection(t, "PRE_TRIP")
    if not t["open"]:
        add_inspection(t, "POST_TRIP")

# defectos al azar del historial
for idx, blocking in random_defect_events:
    t = trips[idx]
    if blocking:
        item, sev, desc, ph, rep = random.choice(DEFECT_POOL_POST_BLOCKING)
        d = add_defect(t["inspections"]["POST_TRIP"], item, sev, desc, ph, rep)
        d["fix_in"] = None  # se resuelve antes de volver a salir (ver unavailable_until)
    else:
        item, desc, ph, rep = random.choice(DEFECT_POOL_NONBLOCKING)
        kind = "POST_TRIP" if item.startswith("post-") else "PRE_TRIP"
        d = add_defect(t["inspections"][kind], item, "NON_BLOCKING", desc, ph, rep)


def last_closed_trip(k):
    return max((t for t in trips if t["vehicle"] == k and not t["open"]), key=lambda t: t["end"])


def last_trip_of(k, kind="PRE_TRIP", before=0):
    return max((t for t in trips if t["vehicle"] == k and t["start"] <= before and (kind == "PRE_TRIP" or not t["open"])),
               key=lambda t: t["start"])


# defectos recientes guionados (quedan abiertos)
t = last_closed_trip("constellation")
D_BLOCK = add_defect(t["inspections"]["POST_TRIP"], "post-fugas", "BLOCKING",
                     "Frenos pierden aire detenido", "demo-fuga-aire-frenos", "aire")
t = last_trip_of("delivery", before=-4)
D_TIRE = add_defect(t["inspections"]["PRE_TRIP"], "ext-neumaticos", "NON_BLOCKING",
                    "Neumático gastado, se ve tela", "demo-neumatico-gastado",
                    "neumatico", with_photo=True)
t = last_trip_of("tector", before=-7)
D_ABS = add_defect(t["inspections"]["PRE_TRIP"], "int-testigos", "NON_BLOCKING",
                   "Testigo de ABS intermitente", "demo-testigo-tablero", "abs", with_photo=True)
t = last_trip_of("scania", before=-1)
D_OPTICA = add_defect(t["inspections"]["PRE_TRIP"], "ext-luces", "NON_BLOCKING",
                      "Farol trasero roto sin tapa", "demo-luz-trasera-rota", "luces", with_photo=True)
t = last_trip_of("sway", before=-12)
D_PARABRISAS = add_defect(t["inspections"]["PRE_TRIP"], "ext-carroceria", "NON_BLOCKING",
                          "Parabrisas rajado (10 cm)", "demo-parabrisas-rajado",
                          "parabrisas", with_photo=True)
SCRIPTED_OPEN = {D_BLOCK["id"], D_TIRE["id"], D_ABS["id"], D_OPTICA["id"], D_PARABRISAS["id"]}

# el odómetro del estacionado queda en el km del último post-trip (ya lo es por construcción)

# ---------------------------------------------------------------- OTs, programaciones y completions
PROVIDERS = {
    "gomeria": ("Gomería Ruta 3", "Walter (mostrador)"),
    "alineacion": ("Alineación y Balanceo Don Torres", "Sergio Torres"),
    "rto": ("Planta RTO Pilar", "Turnos RTO"),
    "vidrieria": ("Vidriería Los Andes", "Marcela"),
    "frenos": ("Frenos Industriales Norte SRL", "Hugo (taller)"),
    "matafuegos": ("Matafuegos Seguridad Total", "Atención comercial"),
    "chapista": ("Chapa y Pintura Hnos. Ferreyra", "Rodolfo Ferreyra"),
    "concesionario": (None, "Recepción de servicio"),
}
DEALER = {"Scania": "Concesionario oficial Scania - Pacheco", "Volvo": "Volvo Trucks Buenos Aires - Tortuguitas",
          "Mercedes-Benz": "Concesionario oficial Mercedes-Benz Camiones - Ruta Panamericana",
          "Iveco": "Concesionario oficial Iveco - Pilar", "Volkswagen": "Concesionario oficial VW Camiones - Garín",
          "Ford": "Taller Diesel Norte"}


def money(lo, hi):
    return round(random.uniform(lo, hi) / 500) * 500


# plan -> (externo?, proveedor, descripción inicial, cierre, gastos [(cat, desc, lo, hi)], foto)
PLAN_WORK = {
    "aceite": (False, None, "Cambio de aceite de motor, filtro de aceite y filtro de combustible.",
               "Se cambió aceite 15W40 y filtros. Sin pérdidas, nivel OK.",
               [("REPUESTO", "Aceite 15W40 x 20 L", 190000, 235000), ("REPUESTO", "Kit filtros aceite + combustible", 85000, 110000)],
               "demo-ot-cambio-aceite"),
    "aceite_ld": (False, None, "Service de aceite larga distancia: aceite sintético, filtro de aceite y prefiltro.",
                  "Se cambió aceite 10W40 sintético (40 L) y filtros. Se reseteó el indicador de service.",
                  [("REPUESTO", "Aceite 10W40 sintético x 40 L", 520000, 610000), ("REPUESTO", "Filtro de aceite + prefiltro", 130000, 160000)],
                  "demo-ot-cambio-aceite"),
    "aire": (False, None, "Reemplazo de filtro de aire primario y de seguridad.",
             "Filtros de aire reemplazados, carcasa limpia.",
             [("REPUESTO", "Filtro de aire primario + seguridad", 150000, 220000)], "demo-ot-filtro-aire"),
    "combustible": (False, None, "Reemplazo de filtros de combustible y purgado del separador de agua.",
                    "Filtros cambiados y sistema purgado. Arranca bien.",
                    [("REPUESTO", "Filtros de combustible + separador de agua", 110000, 160000)], "demo-ot-cambio-aceite"),
    "frenos": (True, "frenos", "Revisión de frenos: medir cintas y tambores, regular y revisar circuito de aire.",
               "Se reemplazaron cintas del eje trasero y se regularon los frenos. Circuito de aire sin pérdidas.",
               [("REPUESTO", "Juego de cintas de freno eje trasero", 380000, 480000),
                ("MANO_DE_OBRA", "Mano de obra frenos", 140000, 190000)], "demo-ot-frenos"),
    "rotacion": (True, "gomeria", "Rotación de neumáticos y control de presión y desgaste.",
                 "Rotación hecha y presiones calibradas. Desgaste parejo.",
                 [("MANO_DE_OBRA", "Rotación y calibración de neumáticos", 60000, 90000)], "demo-ot-neumaticos"),
    "alineacion": (True, "alineacion", "Alineación de tren delantero y balanceo.",
                   "Alineado y balanceado. Se entregó el informe de convergencia.",
                   [("MANO_DE_OBRA", "Alineación y balanceo", 140000, 190000)], "demo-ot-neumaticos"),
    "engrase": (False, None, "Engrase general de chasis, crucetas y quinta rueda.",
                "Engrase completo, sin picos tapados.",
                [("REPUESTO", "Grasa de litio x 5 kg", 45000, 60000)], "demo-ot-engrase"),
    "refrigerante": (False, None, "Cambio de líquido refrigerante y control de mangueras.",
                     "Refrigerante reemplazado, sin pérdidas en mangueras.",
                     [("REPUESTO", "Refrigerante orgánico x 20 L", 120000, 160000)], "demo-ot-taller"),
    "rto": (True, "rto", "Llevar el camión a la planta para la RTO anual.",
            "RTO aprobada. Oblea colocada y certificado guardado en la carpeta del vehículo.",
            [("OTRO", "Arancel RTO camión", 150000, 190000)], "demo-ot-taller"),
    "bateria": (False, None, "Control de baterías, bornes y sistema de carga.",
                "Baterías con carga correcta, bornes limpios y ajustados.", [], "demo-ot-bateria"),
    "caja": (True, "concesionario", "Cambio de aceite de caja y diferencial en concesionario.",
             "Se cambió aceite de caja y diferencial. Sin pérdidas en retenes.",
             [("REPUESTO", "Aceite de caja y diferencial", 350000, 420000),
              ("MANO_DE_OBRA", "Mano de obra concesionario", 160000, 210000)], "demo-ot-taller"),
    "matafuegos": (True, "matafuegos", "Recarga y control de matafuegos de cabina.",
                   "Matafuegos recargados, con tarjeta y vencimiento nuevo.",
                   [("OTRO", "Recarga matafuegos 5 kg x 2", 60000, 80000)], "demo-ot-taller"),
}
DEFECT_WORK = {
    "aire": (True, "frenos", "Revisar circuito de aire de frenos y reemplazar lo que corresponda.",
             "Se cambió la válvula relé del eje trasero y una manguera fisurada. Prueba de estanqueidad OK.",
             [("REPUESTO", "Válvula relé + manguera de aire", 190000, 260000), ("MANO_DE_OBRA", "Mano de obra", 90000, 130000)],
             "demo-ot-frenos"),
    "aceite": (False, None, "Buscar origen de la pérdida de aceite y reparar.",
               "Se reemplazó la junta del cárter y se completó el nivel de aceite.",
               [("REPUESTO", "Junta de cárter", 70000, 110000), ("REPUESTO", "Aceite 15W40 x 5 L", 50000, 60000)],
               "demo-ot-cambio-aceite"),
    "luces": (False, None, "Reemplazar farol / lámparas dañadas.", "Farol reemplazado, luces probadas OK.",
              [("REPUESTO", "Farol trasero completo", 60000, 120000)], "demo-ot-taller"),
    "optica": (False, None, "Reemplazar óptica delantera.", "Óptica nueva colocada y regulada.",
               [("REPUESTO", "Óptica delantera", 250000, 420000)], "demo-ot-taller"),
    "espejo": (False, None, "Reemplazar espejo retrovisor.", "Espejo nuevo colocado y regulado.",
               [("REPUESTO", "Espejo retrovisor completo", 150000, 240000)], "demo-ot-taller"),
    "neumatico": (True, "gomeria", "Reemplazar neumático dañado y revisar el resto del eje.",
                  "Neumático reemplazado por uno nuevo, balanceado y calibrado.",
                  [("REPUESTO", "Neumático 295/80 R22.5", 850000, 1100000), ("MANO_DE_OBRA", "Armado y balanceo", 35000, 50000)],
                  "demo-ot-neumaticos"),
    "parabrisas": (True, "vidrieria", "Cambio de parabrisas.", "Parabrisas nuevo colocado, sin filtraciones.",
                   [("REPUESTO", "Parabrisas", 650000, 880000), ("MANO_DE_OBRA", "Colocación", 100000, 140000)], "demo-ot-taller"),
    "chapa": (True, "chapista", "Reparar abolladura / raspón.", "Chapa enderezada y pintada.",
              [("MANO_DE_OBRA", "Chapa y pintura", 280000, 520000)], "demo-ot-taller"),
    "abs": (False, None, "Diagnosticar testigo de ABS con escáner.", "Sensor de ABS rueda trasera reemplazado, sin códigos de falla.",
            [("REPUESTO", "Sensor ABS", 180000, 260000)], "demo-ot-frenos"),
    "bateria_def": (False, None, "Revisar alternador y baterías.", "Se cambió la correa del alternador. Carga OK.",
                    [("REPUESTO", "Correa de alternador", 60000, 90000)], "demo-ot-bateria"),
    "doc": (False, None, "Reponer documentación en la cabina.", "Se imprimió y dejó la tarjeta del seguro en la cabina.",
            [], "demo-ot-taller"),
}

work_orders, expenses, wo_photos, schedules, completions = [], [], [], [], []
techs = list(TECHS.values())


def new_schedule(v, source, title, day, hh, status, created_day, assignment=None, defect=None, notes=None, updated_day=None,
                 updated_hh=None, created_hh=None):
    s = dict(id=uid("schedule"), vehicle=v, source=source, assignment=assignment, defect=defect, title=title, day=day,
             hh=hh, status=status, notes=notes, created=(created_day, created_hh or random.randint(8, 18)),
             updated=(updated_day if updated_day is not None else created_day,
                      updated_hh if updated_hh is not None else random.randint(8, 18)))
    schedules.append(s)
    return s


def new_wo(v, source, title, work, status, created, day_done=None, schedule=None, defect=None, assignment=None,
           started=None, n_expenses=None, photo_extra=None):
    external, prov_key, description, closing, exp_tpl, photo_name = work
    veh = VBY[v]
    provider, assignee = (None, None)
    tech = None
    if external:
        provider, assignee = PROVIDERS[prov_key]
        if prov_key == "concesionario":
            provider = DEALER[veh["brand"]]
    else:
        tech = random.choice(techs)
    wo = dict(id=uid("workorder"), vehicle=v, source=source, schedule=schedule, defect=defect, assignment=assignment,
              title=title, description=description, exec="EXTERNO" if external else "INTERNO", provider=provider,
              assignee=assignee, tech=tech, status=status, closing=None, created=created, updated=created, finalized=None)
    if status in ("EN_PROCESO", "FINALIZADA"):
        wo["updated"] = started or (created[0], created[1] + 1)
    if status == "FINALIZADA":
        fin = (day_done, random.randint(12, 18))
        wo["closing"], wo["finalized"], wo["updated"] = closing, fin, fin
    exp_list = exp_tpl if n_expenses is None else exp_tpl[:n_expenses]
    if status in ("FINALIZADA", "EN_PROCESO"):
        exp_day = day_done if status == "FINALIZADA" else (started or created)[0]
        for cat, desc, lo, hi in exp_list:
            expenses.append(dict(id=uid("expense"), wo=wo["id"], cat=cat, desc=desc, amount=money(lo, hi),
                                 created=(exp_day, random.randint(9, 16))))
    if status == "FINALIZADA":
        names = [photo_name] + ([photo_extra] if photo_extra else [])
        for i, n in enumerate(names):
            wo_photos.append(dict(id=uid("wophoto"), wo=wo["id"], url=photo(n), created=(day_done, 11 + i)))
    work_orders.append(wo)
    return wo


# mantenimientos del historial: 70 % via calendario + OT, el resto "marcar como hecho" directo
for a in assignments:
    p = PBY[a["plan"]]
    for (day, km) in a["completions"]:
        if day >= 0:
            continue
        via_ot = random.random() < 0.7
        wo_id, notes = None, None
        if via_ot:
            created_day = day - random.randint(3, 10)
            if random.random() < 0.08:
                # programación cancelada y reprogramada
                new_schedule(a["vehicle"], "ASSIGNMENT", p["name"], created_day + 2, 9, "CANCELLED", created_day,
                             assignment=a["id"], notes="Se reprograma: el camión salió de viaje",
                             updated_day=created_day + 1)
                created_day += 1
            s = new_schedule(a["vehicle"], "ASSIGNMENT", p["name"], day, random.choice([8, 9, 10]), "DONE", created_day,
                             assignment=a["id"], updated_day=day, updated_hh=17)
            wo = new_wo(a["vehicle"], "SCHEDULED_MAINTENANCE", p["name"], PLAN_WORK[a["plan"]], "FINALIZADA",
                        (created_day + random.randint(0, 2), random.randint(8, 17)), day_done=day, schedule=s["id"],
                        assignment=a["id"], started=(day, 8))
            wo_id, notes = wo["id"], "Registrado automáticamente al finalizar la orden de trabajo"
        else:
            notes = random.choice([None, "Hecho en base", "Cargado desde la planilla del taller", None])
        completions.append(dict(id=uid("completion"), assignment=a["id"], day=day, km=km, wo=wo_id, notes=notes))

# defectos del historial: todos resueltos por OT
for d in defects:
    if d["id"] in SCRIPTED_OPEN:
        continue
    k = d["vehicle"]
    work = DEFECT_WORK[d["repair"]]
    if d["severity"] == "BLOCKING":
        fix_day = d["day"] + 1
        while not is_idle(k, fix_day):
            fix_day += 1
    else:
        fix_day = d["day"] + random.randint(2, 18)
        while not is_idle(k, fix_day) and fix_day < -1:
            fix_day += 1
    fix_day = min(fix_day, -1)
    d["status"] = "resuelto"
    title = d["description"]
    if work[0] and random.random() < 0.25 and d["severity"] != "BLOCKING":
        # primer intento externo cancelado (proveedor sin repuesto), después se resolvió
        new_wo(k, "DEFECT", title, work, "CANCELADA", (d["day"], d["hh"] + 1 if d["hh"] < 20 else 20), defect=d["id"])
        work_orders[-1]["updated"] = (d["day"] + 1, 10)
        work_orders[-1]["description"] += " (Cancelada: el proveedor no tenía el repuesto, se pidió a otro.)"
    c_day = max(d["day"], fix_day - 3)
    c_hh = min(d["hh"] + 1, 22) if c_day == d["day"] else 10
    if random.random() < 0.5:
        s = new_schedule(k, "DEFECT", title, fix_day, 9, "DONE", c_day, defect=d["id"],
                         updated_day=fix_day, updated_hh=17, created_hh=c_hh)
        new_wo(k, "SCHEDULED_MAINTENANCE", title, work, "FINALIZADA", (c_day, c_hh), day_done=fix_day,
               schedule=s["id"], defect=d["id"], started=(fix_day, 8),
               photo_extra=None)
    else:
        new_wo(k, "DEFECT", title, work, "FINALIZADA", (d["day"], min(d["hh"] + 1, 21)), day_done=fix_day,
               defect=d["id"], started=(fix_day, 8))
    # CAM-134/CAM-155: el defecto queda resuelto cuando se finalizó su OT.
    d["resolved"] = work_orders[-1]["finalized"]

# OTs manuales del historial
MANUAL = [
    ("cargo", "Reparar lona de la caja", -121, True, "chapista", "Se cosió y reforzó la lona. Sin filtraciones.",
     [("MANO_DE_OBRA", "Reparación de lona", 90000, 130000)]),
    ("accelo", "Cerradura puerta acompañante", -88, False, None, "Cerradura nueva colocada, dos llaves entregadas.",
     [("REPUESTO", "Cerradura de puerta", 70000, 95000)]),
    ("scania", "Cubre asientos y alfombras", -40, False, None, "Colocados.",
     [("REPUESTO", "Cubre asientos + alfombras", 110000, 150000)]),
]
for k, title, day, ext, prov, closing, exp in MANUAL:
    while not is_idle(k, day):
        day += 1
    new_wo(k, "MANUAL", title, (ext, prov, title + ".", closing, exp, "demo-ot-taller"), "FINALIZADA", (day - 2, 11),
           day_done=day, started=(day, 8))
wo = new_wo("volvo", "MANUAL", "Instalar rastreo satelital", (True, "concesionario", "Instalar rastreo satelital.",
            None, [], "demo-ot-taller"), "CANCELADA", (-64, 10))
wo["provider"], wo["assignee"] = "Rastreo Satelital Sur", "Guillermo (instalaciones)"
wo["description"] = "Instalar rastreo satelital. (Cancelada: se decidió esperar a la renovación del contrato.)"
wo["updated"] = (-60, 12)

# ---- escenarios abiertos del día 0
# Actros: aceite larga distancia vencido -> programado mañana con OT asignada (interna)
a = ABY[("actros", "aceite_ld")]
s = new_schedule("actros", "ASSIGNMENT", PBY["aceite_ld"]["name"], 1, 8, "SCHEDULED", -2, assignment=a["id"],
                 notes="Entra al taller apenas vuelve de viaje")
new_wo("actros", "SCHEDULED_MAINTENANCE", PBY["aceite_ld"]["name"], PLAN_WORK["aceite_ld"], "ASIGNADA", (-2, 11),
       schedule=s["id"], assignment=a["id"])
work_orders[-1]["tech"] = TECHS["javier.romero"]
# Cargo: RTO vencida -> programada en 3 días, sin OT todavía
a = ABY[("cargo", "rto")]
new_schedule("cargo", "ASSIGNMENT", PBY["rto"]["name"], 3, 7, "SCHEDULED", -3, assignment=a["id"], notes="Turno 7:30 en planta Pilar")
# Accelo: batería por vencer -> programada en 3 días
a = ABY[("accelo", "bateria")]
new_schedule("accelo", "ASSIGNMENT", PBY["bateria"]["name"], 3, 15, "SCHEDULED", -1, assignment=a["id"])
# Atego: frenos por vencer -> programado hoy, OT en proceso con técnico y gastos cargados
a = ABY[("atego", "frenos")]
s = new_schedule("atego", "ASSIGNMENT", PBY["frenos"]["name"], 0, 8, "SCHEDULED", -5, assignment=a["id"])
tpl = PLAN_WORK["frenos"]
new_wo("atego", "SCHEDULED_MAINTENANCE", PBY["frenos"]["name"],
       (False, None, "Revisión de frenos: medir cintas y tambores, regular y revisar circuito de aire.", None,
        [("REPUESTO", "Juego de cintas de freno eje trasero", 380000, 480000)], "demo-ot-frenos"),
       "EN_PROCESO", (-4, 10), schedule=s["id"], assignment=a["id"], started=(0, 8))
work_orders[-1]["tech"] = TECHS["nicolas.benitez"]
wo_photos.append(dict(id=uid("wophoto"), wo=work_orders[-1]["id"], url=photo("demo-ot-frenos"), created=(0, 8)))
# Constellation: defecto bloqueante -> OT directa en proceso
wo = new_wo("constellation", "DEFECT", D_BLOCK["description"], DEFECT_WORK["aire"], "EN_PROCESO",
            (D_BLOCK["day"], min(D_BLOCK["hh"] + 1, 21)), defect=D_BLOCK["id"], started=(D_BLOCK["day"] + 1, 8), n_expenses=1)
wo["exec"], wo["provider"], wo["assignee"], wo["tech"] = "INTERNO", None, None, TECHS["javier.romero"]
wo["description"] = "Revisar circuito de aire de frenos. El camión queda parado hasta resolverlo."
# Delivery: neumático -> programado en 2 días con OT asignada a gomería
s = new_schedule("delivery", "DEFECT", D_TIRE["description"], 2, 9, "SCHEDULED", D_TIRE["day"] + 1, defect=D_TIRE["id"])
new_wo("delivery", "SCHEDULED_MAINTENANCE", D_TIRE["description"], DEFECT_WORK["neumatico"], "ASIGNADA",
       (D_TIRE["day"] + 1, 12), schedule=s["id"], defect=D_TIRE["id"])
# Tector: testigo ABS -> programado en 4 días, sin OT
new_schedule("tector", "DEFECT", D_ABS["description"], 4, 10, "SCHEDULED", D_ABS["day"] + 2, defect=D_ABS["id"])
# Scania (farol) y S-Way (parabrisas): abiertos sin planificar

# ---- CAM-31: checklist pre-trip propio de algunos camiones (agregado antes del día 0)
# El último campo indica si el ítem entra en el checklist del chofer o solo queda cargado en el vehículo.
CHECKLIST_EXTRAS = [
    ("scania", "Estado de la faja de sujeción", "CHECK", "EXTERIOR", True),
    ("actros", "Estado de la faja de sujeción", "CHECK", "EXTERIOR", True),
    ("cargo", "Estado de la lona de la caja", "CHECK", "EXTERIOR", True),
    ("delivery", "Rampa hidráulica trasera", "CHECK", "EXTERIOR", False),
]
checklist_extras = [dict(id=uid("checklistitem"), vehicle=k, label=label, type=typ, section=sec, active=active)
                    for k, label, typ, sec, active in CHECKLIST_EXTRAS]
ITEM_LABELS = {
    "ext-luces": "Luces", "ext-neumaticos": "Neumáticos", "ext-carroceria": "Carrocería, vidrios y espejos",
    "ext-fugas": "Fugas visibles debajo del vehículo", "int-km": "Kilómetros actuales",
    "int-documentacion": "Documentación a bordo (seguro y VTV/RTO)", "int-testigos": "Testigos de tablero",
    "post-danos": "Daños nuevos en la carrocería", "post-luces": "Luces", "post-fugas": "Fugas visibles",
    "post-km": "Kilómetros finales",
}

# CAM-32: descripción larga (lo que el chofer dictaría) de cada defecto, por título. Sin entrada = sin descripción.
DEFECT_DETAILS = {
    "Pérdida de aire en frenos": "Pérdida de aire en el circuito de frenos, se escucha el escape en el eje trasero.",
    "Frenos pierden aire detenido": "Pérdida de aire en el circuito de frenos. El manómetro baja solo con el camión detenido y el motor apagado.",
    "Pérdida de aceite del motor": "Pérdida importante de aceite debajo del motor, deja charco al estacionar.",
    "No encienden luces de freno": "Al pisar el freno no enciende ninguna de las luces traseras. Las de posición sí andan.",
    "Espejo izquierdo arrancado": "El espejo retrovisor izquierdo quedó arrancado por una rama en la ruta. Se sujetó con cinta para volver.",
    "Desgaste irregular neumático": "Neumático trasero con desgaste irregular en el borde externo.",
    "Neumático gastado, se ve tela": "Neumático trasero derecho con desgaste irregular, ya se ve la tela en el borde externo.",
    "Corte en flanco de neumático": "Corte en el flanco del neumático delantero derecho, de unos tres centímetros. No pierde aire.",
    "Óptica delantera derecha rota": "La óptica delantera derecha está rota y alumbra poco de noche.",
    "Farol trasero con tapa partida": "Farol trasero izquierdo con la tapa partida, la lámpara funciona.",
    "Farol trasero roto sin tapa": "Farol trasero izquierdo roto, le falta la tapa. Lo golpearon en la playa de carga.",
    "Parabrisas rajado": "Rajadura en el parabrisas del lado del acompañante.",
    "Parabrisas rajado (10 cm)": "Rajadura chica en el parabrisas del lado del acompañante, de unos diez centímetros. No molesta la visión.",
    "Abolladura lateral de la caja": "Abolladura en el lateral de la caja por una maniobra en el depósito.",
    "Testigo de ABS intermitente": "El testigo de ABS se enciende de forma intermitente, sobre todo al frenar en mojado.",
    "Testigo de batería encendido": "El testigo de carga de batería queda prendido unos minutos después de arrancar.",
}

# ---- CAM-127: documentación de cada camión
# Se genera al final y sin random, así no cambian los ids ni los escenarios de lo de arriba.
# La VTV vence cuando vence su plan "Revisión técnica (RTO)" (vinculado): la del Cargo está vencida y lo deja
# "No disponible". Seguro del Volvo por vencer, el del Scania renovado (con el anterior en el historial) y
# el Accelo sin seguro cargado (obligatorio que falta).
SEGURO_DAYS = {"scania": 345, "volvo": 10, "actros": 230, "sway": 300, "constellation": 120, "atego": 75,
               "tector": 190, "cargo": 160, "delivery": 260}
HABILITACION_DAYS = {"scania": 210, "volvo": 140, "actros": 95, "sway": 280}
TITLES = {"scania", "sway", "delivery"}
documents = []


def new_doc(k, typ, issued, expires, number=None, name=None, file=None, replaced_by=None, created=None):
    d = dict(id=uid("document"), vehicle=k, type=typ, name=name, number=number, issued=issued, expires=expires,
             file=file, replaced_by=replaced_by, created=created if created is not None else (issued if issued is not None else -150))
    documents.append(d)
    return d


for i, v in enumerate(VEHICLES):
    k = v["key"]
    rto = ABY[(k, "rto")]
    new_doc(k, "VTV", rto["last_day"], rto["next_day"], number=f"RTO-{481200 + i * 37}")
    if k in SEGURO_DAYS:
        expires = SEGURO_DAYS[k]
        current = new_doc(k, "SEGURO", expires - 365, expires, number=f"POL-{70315 + i * 11}-{expires % 7}",
                          file=photo("demo-poliza-seguro") if k in ("scania", "volvo") else None)
        if k == "scania":
            new_doc(k, "SEGURO", expires - 730, expires - 365, number=f"POL-{70315 + i * 11}-0",
                    replaced_by=current["id"], created=expires - 730)
    if k in HABILITACION_DAYS:
        new_doc(k, "HABILITACION_CARGA", HABILITACION_DAYS[k] - 365, HABILITACION_DAYS[k], number=f"RUTA-{9100 + i}")
    new_doc(k, "CEDULA_VERDE", None, None, number=f"CV-{v['plate']}", created=-170)
    if k in TITLES:
        new_doc(k, "TITULO", None, None, created=-170)
new_doc("atego", "CEDULA_AZUL", -165, 200, number="CA-31877", name=None)
new_doc("delivery", "OTRO", -320, 45, name="Permiso municipal de carga", number="PM-2210")

# CAM-102/103: desde cuándo está "No disponible" cada camión bloqueado (lo mantiene el backend; acá se siembra).
UNAVAILABLE_SINCE = {"constellation": ts(D_BLOCK["day"], D_BLOCK["hh"], D_BLOCK["mm"]),
                     "cargo": ts(ABY[("cargo", "rto")]["next_day"] + 1, 0)}
assert ABY[("cargo", "rto")]["next_day"] < 0, "la VTV del Cargo tiene que estar vencida"

# CAM-79: los títulos (planes, defectos, programaciones y OTs) no pueden superar 30 caracteres
too_long = [x for x in [p["name"] for p in PLANS] + [d["description"] for d in defects]
            + [s["title"] for s in schedules] + [o["title"] for o in work_orders] if len(x) > 30]
assert not too_long, f"Títulos de más de 30 caracteres: {sorted(set(too_long))}"

# ---- CAM-150/156/157: tipos de vehículo, utilitarios, remolques, plantillas, combustible y cubiertas
# Va al final y con su propio random, así no cambian los ids ni los escenarios de lo de arriba.
rnd = random.Random(157)

TYPES = {"scania": "tractor_4x2", "volvo": "tractor_4x2", "actros": "tractor_6x4", "sway": "tractor_4x2",
         "constellation": "camion_4x2", "atego": "camion_4x2", "tector": "camion_6x4", "cargo": "camion_4x2",
         "delivery": "camion_4x2", "accelo": "camion_4x2"}
for v in VEHICLES:
    v["type"] = TYPES[v["key"]]

# Sin viajes simulados: los km salen de un promedio diario. La Ducato tiene un tipo viejo de texto libre que no
# es del catálogo ("Sin definir"): sin cubiertas hasta que el admin le elija uno.
EXTRA = [
    dict(key="kangoo", plate="AG508UV", brand="Renault", model="Kangoo Furgón 1.6", year=2023, type="utilitario",
         km=48_200, kmday=95, vin="8A1FC1615PL204871", driver="pablo.acosta"),
    dict(key="hilux", plate="AF911PK", brand="Toyota", model="Hilux 2.8 DC 4x4", year=2022, type="utilitario",
         km=73_900, kmday=120, vin="8AJBA3CD9N1730564", driver="diego.sosa"),
    dict(key="ducato", plate="AB204XE", brand="Fiat", model="Ducato Maxicargo", year=2016, type=None, legacy="Minibús",
         km=211_300, kmday=60, vin="93W245H34G2163870", driver=None),
]
# Los remolques no tienen odómetro: suman los km del que los arrastra mientras están enganchados.
TRAILERS = [
    dict(key="semi_helvetica", plate="AC118SR", brand="Helvética", model="Sider 14,5 m", year=2020,
         type="semirremolque_3_ejes", base=231_000, vin="8C9S3E14XL1002318"),
    dict(key="semi_montenegro", plate="AD440TP", brand="Montenegro", model="Furgón térmico", year=2021,
         type="semirremolque_3_ejes", base=168_400, vin="8D3M3F21XM1004402"),
    dict(key="semi_randon", plate="AE771KB", brand="Randon", model="Tolva granelera", year=2022,
         type="semirremolque_3_ejes", base=96_800, vin="9ADG1243XN1771021"),
    dict(key="semi_sola", plate="AB290FN", brand="Sola y Brusa", model="Playo 12 m", year=2017,
         type="semirremolque_2_ejes", base=402_500, vin="8S9B2P12XH1002905"),
    dict(key="semi_bonano", plate="AA512GH", brand="Bonano", model="Batea 2 ejes", year=2015,
         type="semirremolque_2_ejes", base=518_900, vin="8B6B2B15XF1005124"),
    dict(key="acop_ombu", plate="AF350DL", brand="Ombú", model="Acoplado sider 2 ejes", year=2021,
         type="acoplado", base=88_300, vin="8O1A2S21XM1003507"),
    dict(key="acop_cormetal", plate="AC605JR", brand="Cormetal", model="Acoplado playo 2 ejes", year=2016,
         type="acoplado", base=377_200, vin="8C4A2P16XG1006052"),
]
for v in EXTRA + TRAILERS:
    v["id"] = uid("vehicle")
ALLV = {v["key"]: v for v in VEHICLES + EXTRA + TRAILERS}

COUPLINGS = [  # (remolque, el que lo arrastra, desde, hasta) -- None: sigue enganchado
    ("semi_helvetica", "volvo", -200, -75), ("semi_helvetica", "scania", -75, None),
    ("semi_montenegro", "scania", -200, -75), ("semi_montenegro", "volvo", -75, None),
    ("semi_bonano", "actros", -200, -100), ("semi_randon", "actros", -100, None),
    ("semi_sola", "sway", -200, None),
    ("acop_ombu", "tector", -200, None),
    ("acop_cormetal", "cargo", -200, -30),   # el Cargo quedó parado por la VTV: el acoplado espera en base
]
couplings = [dict(id=uid("coupling"), trailer=tr, towing=tw, start=s, end=e,
                  by="admin" if s <= HISTORY_START else ALLV[tw]["driver"]) for tr, tw, s, e in COUPLINGS]


def vkm(k, day):
    v = ALLV[k]
    day = min(day, 0)
    if k in VBY:
        return km_at(k, day)
    if "kmday" in v:
        return max(0, v["km"] + v["kmday"] * day)
    return v["base"] + sum(vkm(c["towing"], min(day, c["end"] if c["end"] is not None else 0)) - vkm(c["towing"], c["start"])
                           for c in couplings if c["trailer"] == k and c["start"] <= day)


for v in EXTRA:
    v["odometer"] = v["km"]
for v in TRAILERS:
    v["odometer"] = vkm(v["key"], 0)

# ---- CAM-150: plantillas de checklist. Aplicar una copia sus controles al vehículo; los que ya tiene se saltean.
TEMPLATES = [
    dict(name="Tractor con semirremolque", day=-40, applied=["scania", "volvo", "actros", "sway"], items=[
        ("Estado de la faja de sujeción", "CHECK", "EXTERIOR"), ("Quinta rueda trabada y engrasada", "CHECK", "EXTERIOR"),
        ("Mangueras de aire y cable del semi", "CHECK", "EXTERIOR"), ("Patas de apoyo levantadas", "CHECK", "EXTERIOR")]),
    dict(name="Reparto urbano", day=-25, applied=["delivery", "accelo"], items=[
        ("Puerta trasera y traba de la caja", "CHECK", "EXTERIOR"), ("Matafuegos cargado y a mano", "CHECK", "INTERIOR"),
        ("Calzas para estacionar", "CHECK", "EXTERIOR")]),
    dict(name="Utilitario", day=-15, applied=["kangoo", "hilux"], items=[
        ("Rueda de auxilio y crique", "CHECK", "EXTERIOR"), ("Botiquín y balizas", "CHECK", "INTERIOR")]),
    # Sin aplicar todavía: se ve en Plantillas y se puede aplicar en la demo.
    dict(name="Camión con acoplado", day=-5, applied=[], items=[
        ("Lanza y perno del acoplado", "CHECK", "EXTERIOR"), ("Luces del acoplado", "CHECK", "EXTERIOR"),
        ("Presión de aire del acoplado (bar)", "NUMBER", "EXTERIOR")]),
]
template_items = []
for tpl in TEMPLATES:
    tpl["id"] = uid("checklisttemplate")
    for pos, (label, typ, sec) in enumerate(tpl["items"]):
        template_items.append(dict(id=uid("checklisttemplateitem"), template=tpl["id"], label=label, type=typ, section=sec,
                                   position=pos))
    for k in tpl["applied"]:
        for label, typ, sec in tpl["items"]:
            if any(c["vehicle"] == k and c["label"].lower() == label.lower() and c["section"] == sec for c in checklist_extras):
                continue
            checklist_extras.append(dict(id=uid("checklistitem"), vehicle=k, label=label, type=typ, section=sec,
                                         active=True, created=tpl["day"]))

# ---- CAM-156: cargas de combustible. Tanque lleno la mayoría; algunas parciales en el medio de un tramo.
CONS = {"larga": 33.0, "regional": 27.0, "urbano": 19.0, "kangoo": 8.4, "hilux": 10.6}
FILL_KM = {"larga": (1300, 1900), "regional": (650, 950), "urbano": (380, 520), "kangoo": (420, 560), "hilux": (520, 700)}
ANOMALY = ("volvo", -12)   # el primer tanque lleno desde ese día consume un 45 % más (aviso)
FUEL_FROM = {"accelo": -20}  # pocas cargas: todavía no hay consumo calculado


def fuel_price(day):
    return 1180 + (day - HISTORY_START) * 1.9   # $/L, sube con la inflación


fuel_loads = []
for k in [v["key"] for v in VEHICLES] + ["kangoo", "hilux"]:
    v = ALLV[k]
    cls = v.get("cls", k)
    start = FUEL_FROM.get(k, HISTORY_START + rnd.randint(0, 5))
    last_km = vkm(k, start)
    fuel_loads.append(dict(id=uid("fuelload"), vehicle=k, day=start, hh=rnd.randint(6, 9), mm=rnd.randint(0, 59),
                           km=last_km, liters=round(rnd.uniform(0.5, 0.8) * (600 if cls == "larga" else 300 if cls != k else 50), 1),
                           full=True))
    seg_km_start, seg_partials = last_km, 0.0
    seg_cons = CONS[cls] * rnd.uniform(0.94, 1.06)
    target = rnd.randint(*FILL_KM[cls])
    anomalous_done = False
    for day in range(start + 1, 0):
        # un viaje largo suma todos sus km el día que termina: las cargas caen en el medio, cada `target` km
        hh = rnd.randint(6, 9)
        while vkm(k, day) - last_km >= target:
            km = last_km + target
            partial = rnd.random() < 0.18 and km - seg_km_start < target + 1
            if partial:
                liters = round((km - last_km) * seg_cons / 100 * rnd.uniform(0.55, 0.8), 1)
                seg_partials += liters
            else:
                mult = 1.0
                if k == ANOMALY[0] and day >= ANOMALY[1] and not anomalous_done:
                    mult, anomalous_done = 1.45, True
                liters = round((km - seg_km_start) * seg_cons * mult / 100 - seg_partials, 1)
            fuel_loads.append(dict(id=uid("fuelload"), vehicle=k, day=day, hh=min(hh, 22), mm=rnd.randint(0, 59),
                                   km=km, liters=liters, full=not partial))
            hh += rnd.randint(3, 6)
            last_km = km
            target = rnd.randint(*FILL_KM[cls])
            if not partial:
                seg_km_start, seg_partials = km, 0.0
                seg_cons = CONS[cls] * rnd.uniform(0.94, 1.06)
for f in fuel_loads:
    f["price"] = round(fuel_price(f["day"]), 2)
    f["amount"] = round(f["liters"] * f["price"], 2)
    f["by"] = ALLV[f["vehicle"]]["driver"]


def fuel_alerts():
    # mismo cálculo que FuelLoadService: tramo vs. los últimos 10 (mínimo 3), litros totales / km totales
    found = []
    for k in {f["vehicle"] for f in fuel_loads}:
        loads = sorted([f for f in fuel_loads if f["vehicle"] == k], key=lambda f: (f["day"], f["hh"], f["mm"]))
        segs, last_full, liters = [], None, 0.0
        for f in loads:
            if last_full is None:
                last_full = f if f["full"] else None
                continue
            liters += f["liters"]
            if not f["full"]:
                continue
            seg = (f, f["km"] - last_full["km"], liters)
            prev = segs[-10:]
            if len(prev) >= 3:
                avg = sum(s[2] for s in prev) * 100 / sum(s[1] for s in prev)
                cons = seg[2] * 100 / seg[1]
                if cons > avg * 1.3:
                    found.append((f, round(cons, 2), round(avg, 2), seg[1]))
            segs.append(seg)
            last_full, liters = f, 0.0
    return found


FUEL_ALERTS = fuel_alerts()
assert [a[0]["vehicle"] for a in FUEL_ALERTS] == ["volvo"], f"tiene que haber un solo aviso de consumo: {FUEL_ALERTS}"
for f, cons, avg, _ in FUEL_ALERTS:
    f["alert"], f["avg"] = cons, avg

# ---- CAM-157: cubiertas. Cada vehículo con tipo arranca con todas sus posiciones montadas; después rotaciones,
# cambios y bajas guionados, mediciones de dibujo en dos pre-viajes y una cubierta por debajo del mínimo.
AXLES = {"utilitario": "SS", "camion_4x2": "SD", "camion_6x4": "SDD", "tractor_4x2": "SD", "tractor_6x4": "SDD",
         "semirremolque_2_ejes": "DD", "semirremolque_3_ejes": "DDD", "acoplado": "DD"}
MIN_TREAD = 1.6


def positions_of(t):
    out = []
    for i, w in enumerate(AXLES[t], 1):
        out += [f"e{i}-izq", f"e{i}-der"] if w == "S" else [f"e{i}-izq-ext", f"e{i}-izq-int", f"e{i}-der-int", f"e{i}-der-ext"]
    return out + ["auxilio"]


def position_label(t, p):
    # igual que VehicleType.labelOf
    if p == "auxilio":
        return "Auxilio"
    parts = p.split("-")
    axle, side = int(parts[0][1:]), "izquierda" if parts[1] == "izq" else "derecha"
    trailer = t.startswith("semi") or t == "acoplado"
    base = f"Eje {axle} {side}" if trailer or (axle > 1 and len(AXLES[t]) > 2) else ("Delantera " if axle == 1 else "Trasera ") + side
    return base + {"ext": " exterior", "int": " interior"}.get(parts[2] if len(parts) > 2 else "", "")


BRANDS = {  # marca: (modelos por uso, factor de costo, desgaste mm cada 1000 km)
    "Michelin": ({"steer": "X Multi Z", "drive": "X Multi D", "trailer": "X Multi T", "light": "X Multi Z", "util": "Agilis"}, 1.15, 0.070),
    "Bridgestone": ({"steer": "R268", "drive": "M729", "trailer": "R168", "light": "R268", "util": "Dueler A/T 001"}, 1.08, 0.075),
    "Pirelli": ({"steer": "FR85", "drive": "TR85", "trailer": "ST01", "light": "FR85", "util": "Chrono"}, 1.05, 0.080),
    "Fate": ({"steer": "Sentiva Truck", "drive": "Ecoplus", "trailer": "Sentiva Trailer", "light": "Sentiva Truck", "util": "Eximia"}, 0.85, 0.095),
    "Firestone": ({"steer": "FS591", "drive": "FD691", "trailer": "FT833", "light": "FS591", "util": "Destination"}, 0.92, 0.085),
}
FLEET_BRANDS = {"larga": ["Michelin", "Bridgestone"], "regional": ["Pirelli", "Fate", "Firestone"],
                "urbano": ["Fate", "Firestone"], "trailer": ["Bridgestone", "Fate", "Firestone"]}
SIZE = {"delivery": "215/75 R17.5", "accelo": "215/75 R17.5", "kangoo": "195/65 R15", "hilux": "265/65 R17"}
BASE_COST = {"295/80 R22.5": 590_000, "215/75 R17.5": 285_000, "195/65 R15": 118_000, "265/65 R17": 215_000}
INITIAL = {"steer": 16.0, "drive": 19.0, "trailer": 15.0, "light": 14.0, "util": 8.0}
MOUNT_FROM = {"larga": (-520, -250), "regional": (-640, -320), "urbano": (-720, -380), "util": (-330, -150),
              "trailer": (-420, -200)}

tires, mountings = [], []
_code = [0]


def new_tire(k, use, brand, day):
    v = ALLV[k]
    size = SIZE.get(k, "295/80 R22.5")
    _code[0] += 1
    models, cost_f, wear = BRANDS[brand]
    t = dict(id=uid("tire"), code=f"{brand[:2].upper()}-{24 + (day > -120)}-{_code[0]:04d}", brand=brand,
             model=models[use], size=size, purchase=day - rnd.randint(3, 30),
             cost=round(BASE_COST[size] * cost_f * rnd.uniform(0.95, 1.05), -3), initial=INITIAL[use],
             wear=1.6 * wear * (1.15 if use == "drive" else 0.8 if use == "trailer" else 1.4 if use == "util" else 1.0),
             status="STOCK", retired=None, reason=None, alerted=None)
    tires.append(t)
    return t


def use_of(k, p):
    t = ALLV[k]["type"]
    if t == "utilitario":
        return "util"
    if k in SIZE:
        return "light"
    if t.startswith("semi") or t == "acoplado":
        return "trailer"
    return "steer" if p.startswith("e1-") else "drive"


def mount(k, p, tire, day, hh=9):
    m = dict(id=uid("tiremounting"), tire=tire["id"], vehicle=k, position=p, day=day, hh=hh, km=vkm(k, day),
             removed=None, removed_km=None)
    mountings.append(m)
    tire["status"] = "MOUNTED"
    return m


def unmount(m, day, hh=10):
    m["removed"], m["removed_km"] = (day, hh), vkm(m["vehicle"], day)


current = {}   # (vehículo, posición) -> montaje abierto
for v in VEHICLES + EXTRA + TRAILERS:
    if v["type"] is None:
        continue
    k = v["key"]
    group = "trailer" if v in TRAILERS else "util" if v["type"] == "utilitario" else v["cls"]
    brands = FLEET_BRANDS.get(group, ["Pirelli", "Bridgestone"])
    by_use = {}
    for p in positions_of(v["type"]):
        use = use_of(k, p)
        brand = by_use.setdefault(use, rnd.choice(brands)) if group != "util" else ("Bridgestone" if k == "hilux" else "Pirelli")
        day = rnd.randint(*MOUNT_FROM[group]) if p != "auxilio" else rnd.randint(-700, -400)
        current[(k, p)] = mount(k, p, new_tire(k, use, brand, day), day)

TIRE = {t["id"]: t for t in tires}
EVENTS = []   # (día, tipo, vehículo, posición[, destino, motivo, se repone])
for k in [v["key"] for v in VEHICLES] + ["kangoo"]:
    rot = ABY[(k, "rotacion")]["last_day"] if k in VBY else -70
    if ALLV[k]["type"] == "utilitario":
        EVENTS += [(rot, "rotate", k, "e1-izq", "e2-izq"), (rot, "rotate", k, "e1-der", "e2-der")]
    else:
        EVENTS += [(rot, "rotate", k, "e1-izq", "e2-izq-ext"), (rot, "rotate", k, "e1-der", "e2-der-ext")]
EVENTS += [
    (-140, "remove", "scania", "e2-der-int", "RETIRED", "Reventón en ruta", True),
    (-110, "remove", "actros", "e3-izq-ext", "RETIRED", "Corte en el flanco", True),
    (-80, "remove", "semi_helvetica", "e2-der-ext", "RETIRED", "Pinchadura irreparable", True),
    (-60, "remove", "volvo", "e1-izq", "RETIRED", "Desgaste irregular por alineación", True),
    (-50, "remove", "tector", "e1-izq", "STOCK", None, True),
    (-45, "remove", "atego", "e2-izq-ext", "RETIRED", "Llegó al mínimo de dibujo", True),
    (-30, "remove", "cargo", "auxilio", "STOCK", None, False),   # el auxilio queda vacío
    (-20, "remove", "kangoo", "e1-der", "RETIRED", "Corte en el flanco", True),
]
for ev in sorted(EVENTS, key=lambda e: e[0]):
    day, kind, k = ev[0], ev[1], ev[2]
    if kind == "rotate":
        a, b = current[(k, ev[3])], current[(k, ev[4])]
        if a["day"] >= day or b["day"] >= day:
            continue
        unmount(a, day), unmount(b, day)
        current[(k, ev[3])] = mount(k, ev[3], TIRE[b["tire"]], day, 10)
        current[(k, ev[4])] = mount(k, ev[4], TIRE[a["tire"]], day, 10)
    else:
        p, dest, reason, replace = ev[3], ev[4], ev[5], ev[6]
        m = current.pop((k, p))
        unmount(m, day, 11)
        old = TIRE[m["tire"]]
        old["status"], old["reason"] = dest, reason
        old["retired"] = (day, 11) if dest == "RETIRED" else None
        if replace:
            nt = new_tire(k, use_of(k, p), old["brand"] if rnd.random() < 0.6 else rnd.choice(list(BRANDS)), day)
            TIRE[nt["id"]] = nt
            current[(k, p)] = mount(k, p, nt, day, 11)
for brand, use, size in [("Michelin", "drive", None), ("Michelin", "drive", None), ("Fate", "drive", None),
                         ("Fate", "drive", None), ("Bridgestone", "steer", None), ("Pirelli", "util", "kangoo")]:
    nt = new_tire(size or "scania", use, brand, -rnd.randint(4, 12))
    TIRE[nt["id"]] = nt


def tire_km(tid, day):
    return sum(vkm(m["vehicle"], min(day, m["removed"][0] if m["removed"] else 0)) - m["km"]
               for m in mountings if m["tire"] == tid and m["position"] != "auxilio" and m["day"] <= day)


def depth(t, day):
    return max(2.2, round(t["initial"] - t["wear"] * tire_km(t["id"], day) / 1000 + rnd.uniform(-0.3, 0.3), 1))


def mounted_on(k, day):
    return [m for m in mountings if m["vehicle"] == k and m["position"] != "auxilio" and m["day"] <= day
            and (m["removed"] is None or m["removed"][0] > day)]


def nearest_pre_trip(k, day):
    pre = [i for i in inspections if i["vehicle"] == k and i["type"] == "PRE_TRIP"]
    return min(pre, key=lambda i: abs(i["day"] - day)) if pre else None


tread_readings = []
D_TIRE_INS = next(i for i in inspections if any(a["id"] == D_TIRE["answer"] for a in i["answers"]))
LOW = current[("delivery", "e2-der-ext")]   # la del defecto "Neumático gastado, se ve tela"
FORCED = {LOW["tire"]: [2.9, 1.9, 1.2]}


def read_all(k, day, hh, mm, by, ins_id, rounds_idx):
    for m in mounted_on(k, day):
        d = depth(TIRE[m["tire"]], day)
        if m["tire"] in FORCED:
            d = FORCED[m["tire"]][rounds_idx]
        tread_readings.append(dict(id=uid("treadreading"), tire=m["tire"], vehicle=k, position=m["position"], depth=d,
                                   day=day, hh=hh, mm=mm + 2 if mm < 57 else mm, by=by, inspection=ins_id))


for v in VEHICLES:
    k = v["key"]
    seen = set()
    for idx, target in enumerate([-62, -14]):
        ins = nearest_pre_trip(k, target)
        if ins and ins["id"] not in seen and ins["id"] != D_TIRE_INS["id"]:
            seen.add(ins["id"])
            read_all(k, ins["day"], ins["hh"], ins["mm"], ins["driver"], ins["id"], idx)
read_all("delivery", D_TIRE_INS["day"], D_TIRE_INS["hh"], D_TIRE_INS["mm"], D_TIRE_INS["driver"], D_TIRE_INS["id"], 2)
for k, day in [("kangoo", -35), ("semi_helvetica", -40), ("semi_montenegro", -40), ("acop_ombu", -40)]:
    read_all(k, day, 15, 0, None, None, 0)
low = [r for r in tread_readings if r["depth"] < MIN_TREAD]
assert [r["tire"] for r in low] == [LOW["tire"]], "solo la cubierta del Delivery tiene que quedar bajo el mínimo"
TIRE[LOW["tire"]]["alerted"] = (low[0]["day"], low[0]["hh"], low[0]["mm"])


def ar(n, decimals=1):
    s = f"{n:,.{decimals}f}"
    return s.replace(",", "X").replace(".", ",").replace("X", ".")


def tire_label(t):
    return f"{t['brand']} {t['model']} N° {t['code']}"


notifications = []
for f, cons, avg, km in FUEL_ALERTS:
    plate = ALLV[f["vehicle"]]["plate"]
    notifications.append(dict(type="FUEL_CONSUMPTION_ANOMALY", title=f"Consumo de combustible alto en {plate}",
                              message=f"{plate} consumió {ar(cons)} L cada 100 km en los últimos {ar(km, 0)} km, un "
                                      f"{round((cons - avg) * 100 / avg)}% más que su promedio de {ar(avg)} L.",
                              link=f"/vehiculos/{ALLV[f['vehicle']]['id']}/combustible", at=(f["day"], f["hh"], f["mm"])))
lt = TIRE[LOW["tire"]]
notifications.append(dict(type="TIRE_TREAD_LOW", title=f"Dibujo bajo en {VBY['delivery']['plate']}",
                          message=f"La cubierta {tire_label(lt)} ({position_label('camion_4x2', 'e2-der-ext')}) de "
                                  f"{VBY['delivery']['plate']} tiene {ar(1.2)} mm de dibujo, menos del mínimo de "
                                  f"{ar(MIN_TREAD)} mm. Lo midió {D_TIRE_INS['driver']}.",
                          link=f"/vehiculos/{VBY['delivery']['id']}/neumaticos", at=lt["alerted"]))


# ---------------------------------------------------------------- SQL
KNOWN_COLUMNS = {
    "users": "id username password_hash role",
    "vehicles": "id plate brand model vehicle_type odometer_km year chassis_number active unavailable_since",
    "trips": "id vehicle_id status started_at ended_at opened_by_id opened_by_name closed_by_id closed_by_name",
    "inspections": "id trip_id vehicle_id driver_id driver_name type timestamp odometer_km notes has_blocking_defect",
    "inspection_answers": "id inspection_id item_id item_label outcome number_value",
    "defects": "id inspection_answer_id severity description details photo_url created_at status resolved_at",
    "maintenance_plans": "id name category interval_type interval_km interval_days active document_type",
    "vehicle_maintenance_assignments": "id vehicle_id maintenance_plan_id last_done_km last_done_date next_due_km next_due_date active",
    "maintenance_completions": "id assignment_id completed_at completed_km work_order_id notes",
    "scheduled_maintenances": "id vehicle_id source_type assignment_id defect_id title scheduled_at status notes created_at updated_at",
    "work_orders": "id vehicle_id source_type scheduled_maintenance_id defect_id assignment_id title description execution_type "
                   "external_provider assignee technician_id status closing_description created_at updated_at finalized_at",
    "work_order_expenses": "id work_order_id category description amount created_at",
    "work_order_photos": "id work_order_id photo_url created_at",
    "vehicle_checklist_items": "id vehicle_id label type section active created_at",
    "vehicle_disabled_checklist_items": "id vehicle_id base_item_id",
    "vehicle_documents": "id vehicle_id type name number issued_at expires_at file_url replaced_by_id active created_at",
    "checklist_templates": "id name created_at",
    "checklist_template_items": "id template_id label type section position",
    "fuel_loads": "id vehicle_id loaded_at liters amount odometer_km full_tank created_by created_at "
                  "alert_liters_per_100_km alert_average_liters_per_100_km",
    "tires": "id code brand model size purchase_date cost initial_tread_mm status retired_at retired_reason tread_alerted_at created_at",
    "tire_mountings": "id tire_id vehicle_id position mounted_at mounted_km removed_at removed_km",
    "tire_tread_readings": "id tire_id vehicle_id position depth_mm measured_at measured_by measured_by_name inspection_id",
    "trailer_couplings": "id trailer_id towing_id coupled_at uncoupled_at coupled_by_name",
}
out = []
w = out.append
w("-- Datos demo: 10 camiones con 6 meses de operación simulada (viajes, inspecciones DVIR, defectos,")
w("-- planes de mantenimiento, programaciones y órdenes de trabajo), utilitarios, remolques enganchados, plantillas")
w("-- de checklist, cargas de combustible y cubiertas. Generado por docs/db/generate-seed-demo.py.")
w("--")
w("-- ATENCIÓN: borra TODOS los datos operativos y todos los usuarios salvo 'admin'.")
w("-- Correr con el backend apagado:")
w("--   psql -U postgres -d TIP -v ON_ERROR_STOP=1 -f docs/db/seed-demo.sql")
w("-- y copiar las fotos: Copy-Item docs\\db\\demo-photos\\* uploads\\photos\\")
w("--")
w("-- Fechas relativas a current_date: el día que se corre es el \"hoy\" de la demo.")
w("-- Requiere haber levantado el backend al menos una vez con la versión actual (crea las tablas).")
w("-- Usuarios: admin/admin123 · choferes (chofer123): " + ", ".join(DRIVERS) + " · técnicos (tecnico123): " + ", ".join(TECHS))
w("")
w("set client_encoding = 'UTF8';")
w("begin;")
w("")
w("create function pg_temp.d(n int) returns date language sql stable as $$ select current_date + n $$;")
w("create function pg_temp.ts(n int, t text) returns timestamptz language sql stable as")
w("  $$ select ((current_date + n) + t::time) at time zone 'America/Argentina/Buenos_Aires' $$;")
w("")
w("-- Columnas viejas que ya no están en las entidades (Hibernate update nunca las borra): si son")
w("-- NOT NULL sin default bloquean los inserts, así que se les saca el NOT NULL.")
w("do $$")
w("declare c record;")
w("begin")
w("  for c in select table_name, column_name from information_schema.columns")
w("           where table_schema = current_schema() and is_nullable = 'NO' and column_default is null")
w("             and (table_name, column_name) not in (")
pairs_ = [f"('{t}','{c}')" for t, cols in KNOWN_COLUMNS.items() for c in cols.split()]
w("               " + ",\n               ".join(", ".join(pairs_[i:i+5]) for i in range(0, len(pairs_), 5)))
w("             ) and table_name in (" + ", ".join(f"'{t}'" for t in KNOWN_COLUMNS) + ")")
w("  loop")
w("    execute format('alter table %I alter column %I drop not null', c.table_name, c.column_name);")
w("    raise notice 'Columna vieja %.% ya no es NOT NULL', c.table_name, c.column_name;")
w("  end loop;")
w("end $$;")
w("")
w("-- ddl-auto no actualiza el CHECK que Hibernate arma para un enum en una base que ya existe: un valor")
w("-- nuevo da 500. Se borran (el valor lo valida el enum de Java): en_espera de las OTs (CAM-148) y los")
w("-- tipos de aviso nuevos (CAM-152) y el")
w("-- reporte en viaje de las inspecciones (CAM-139).")
w("alter table if exists work_orders drop constraint if exists work_orders_status_check;")
w("alter table if exists notifications drop constraint if exists notifications_type_check;")
w("alter table if exists notification_settings drop constraint if exists notification_settings_type_check;")
w("alter table if exists inspections drop constraint if exists inspections_type_check;")
w("-- CAM-157: las posiciones de las cubiertas cambiaron de nombre, y el tipo de vehículo reemplazó a tire_layout.")
w("alter table if exists tire_mountings drop constraint if exists tire_mountings_position_check;")
w("alter table if exists tire_tread_readings drop constraint if exists tire_tread_readings_position_check;")
w("alter table if exists vehicles drop column if exists tire_layout;")
w("")
w("truncate table work_order_photos, work_order_expenses, work_orders, scheduled_maintenances, maintenance_completions,")
w("  vehicle_checklist_items, vehicle_disabled_checklist_items, vehicle_documents, document_alert_log, recurring_defect_alerts,")
w("  vehicle_maintenance_assignments, maintenance_plans, defects, inspection_answers, inspections, trips,")
w("  checklist_template_items, checklist_templates, fuel_loads, tire_tread_readings, tire_mountings, tires,")
w("  trailer_couplings, vehicles cascade;")
w("delete from notifications where type in ('FUEL_CONSUMPTION_ANOMALY', 'TIRE_TREAD_LOW');")
w("")
w("-- Usuarios")
w("delete from users where username <> 'admin';")
w(f"insert into users (id, username, password_hash, role) values (gen_random_uuid(), 'admin', {q(H_ADMIN)}, 'ADMIN')")
w("  on conflict (username) do nothing;")
rows = [f"  ({q(DRIVER_IDS[u])}, {q(u)}, {q(H_CHOFER)}, 'CHOFER')" for u in DRIVERS]
rows += [f"  ({q(i)}, {q(u)}, {q(H_TECNICO)}, 'TECNICO')" for u, i in TECHS.items()]
w("insert into users (id, username, password_hash, role) values\n" + ",\n".join(rows) + ";")
w("")


def insert(table, cols, rows_):
    if not rows_:
        return
    for i in range(0, len(rows_), 500):
        chunk = rows_[i:i + 500]
        w(f"insert into {table} ({', '.join(cols)}) values\n" + ",\n".join("  (" + ", ".join(r) + ")" for r in chunk) + ";")
    w("")


w("-- Vehículos")
insert("vehicles", ["id", "plate", "brand", "model", "vehicle_type", "odometer_km", "year", "chassis_number", "active",
                    "unavailable_since"],
       [[q(v["id"]), q(v["plate"]), q(v["brand"]), q(v["model"]), q(v["type"] or v.get("legacy")), str(v["odometer"]),
         str(v["year"]), q(v["vin"]), "true", UNAVAILABLE_SINCE.get(v["key"], "null")] for v in VEHICLES + EXTRA + TRAILERS])
w("-- Catálogo de planes")
insert("maintenance_plans", ["id", "name", "category", "interval_type", "interval_km", "interval_days", "active",
                             "document_type"],
       [[q(p["id"]), q(p["name"]), q(p["category"]), q(p["type"]), str(p["km"]) if p["km"] else "null",
         str(p["days"]) if p["days"] else "null", "true" if p.get("active", True) else "false", q(p.get("doc"))]
        for p in PLANS])
w("-- Asignaciones (last/next = cache de la última completion)")
insert("vehicle_maintenance_assignments",
       ["id", "vehicle_id", "maintenance_plan_id", "last_done_km", "last_done_date", "next_due_km", "next_due_date", "active"],
       [[q(a["id"]), q(VBY[a["vehicle"]]["id"]), q(PBY[a["plan"]]["id"]), str(a["last_km"]), dt(a["last_day"]),
         str(a["next_km"]) if a["next_km"] is not None else "null",
         dt(a["next_day"]) if a["next_day"] is not None else "null", "true"] for a in assignments])
w("-- Viajes")
# CAM-87: cada viaje guarda quién lo abrió y quién lo cerró (acá, siempre su chofer).
insert("trips", ["id", "vehicle_id", "status", "started_at", "ended_at", "opened_by_id", "opened_by_name",
                 "closed_by_id", "closed_by_name"],
       [[q(t["id"]), q(VBY[t["vehicle"]]["id"]), "'OPEN'" if t["open"] else "'CLOSED'",
         ts(t["start"], t["inspections"]["PRE_TRIP"]["hh"], t["inspections"]["PRE_TRIP"]["mm"]),
         "null" if t["open"] else ts(t["end"], t["inspections"]["POST_TRIP"]["hh"], t["inspections"]["POST_TRIP"]["mm"]),
         q(DRIVER_IDS[t["driver"]]), q(t["driver"]),
         "null" if t["open"] else q(DRIVER_IDS[t["driver"]]), "null" if t["open"] else q(t["driver"])]
        for t in trips])
w("-- Inspecciones DVIR")
insert("inspections", ["id", "trip_id", "vehicle_id", "driver_id", "driver_name", "type", '"timestamp"', "odometer_km",
                       "notes", "has_blocking_defect"],
       # CAM-85: el chofer de la inspección es el usuario (id y username), como lo guarda el backend.
       [[q(i["id"]), q(i["trip"]), q(VBY[i["vehicle"]]["id"]), q(DRIVER_IDS[i["driver"]]), q(i["driver"]), q(i["type"]),
         ts(i["day"], i["hh"], i["mm"]), str(i["km"]), q(i["notes"]), "true" if i["blocking"] else "false"]
        for i in inspections])
insert("inspection_answers", ["id", "inspection_id", "item_id", "item_label", "outcome", "number_value"],
       [[q(a["id"]), q(a["inspection"]), q(a["item"]), q(ITEM_LABELS[a["item"]]), q(a["outcome"]),
         str(a["number"]) if a["number"] is not None else "null"]
        for i in inspections for a in i["answers"]])
w("-- Checklist pre-trip propio de cada camión (CAM-31)")
insert("vehicle_checklist_items", ["id", "vehicle_id", "label", "type", "section", "active", "created_at"],
       [[q(c["id"]), q(ALLV[c["vehicle"]]["id"]), q(c["label"]), q(c["type"]), q(c["section"]), "true" if c["active"] else "false",
         ts(c.get("created", -30), 10)] for c in checklist_extras])
w("-- Defectos")
insert("defects", ["id", "inspection_answer_id", "severity", "description", "details", "photo_url", "created_at", "status",
                   "resolved_at"],
       [[q(d["id"]), q(d["answer"]), q(d["severity"]), q(d["description"]), q(DEFECT_DETAILS.get(d["description"])),
         q(d["photo"]), ts(d["day"], d["hh"], d["mm"]),
         q(d["status"]), ts(*d["resolved"]) if d.get("resolved") else "null"] for d in defects])
w("-- Programaciones (calendario)")
insert("scheduled_maintenances", ["id", "vehicle_id", "source_type", "assignment_id", "defect_id", "title", "scheduled_at",
                                  "status", "notes", "created_at", "updated_at"],
       [[q(s["id"]), q(VBY[s["vehicle"]]["id"]), q(s["source"]), q(s["assignment"]), q(s["defect"]), q(s["title"]),
         ts(s["day"], s["hh"], 0 if s["hh"] != 7 else 30), q(s["status"]), q(s["notes"]), ts(*s["created"]),
         ts(*s["updated"])] for s in schedules])
w("-- Órdenes de trabajo")
insert("work_orders", ["id", "vehicle_id", "source_type", "scheduled_maintenance_id", "defect_id", "assignment_id", "title",
                       "description", "execution_type", "external_provider", "assignee", "technician_id", "status",
                       "closing_description", "created_at", "updated_at", "finalized_at"],
       [[q(o["id"]), q(VBY[o["vehicle"]]["id"]), q(o["source"]), q(o["schedule"]), q(o["defect"]), q(o["assignment"]),
         q(o["title"]), q(o["description"]), q(o["exec"]), q(o["provider"]), q(o["assignee"]), q(o["tech"]), q(o["status"]),
         q(o["closing"]), ts(*o["created"]), ts(*o["updated"]), ts(*o["finalized"]) if o["finalized"] else "null"]
        for o in work_orders])
insert("work_order_expenses", ["id", "work_order_id", "category", "description", "amount", "created_at"],
       [[q(e["id"]), q(e["wo"]), q(e["cat"]), q(e["desc"]), f"{e['amount']:.2f}", ts(*e["created"])] for e in expenses])
insert("work_order_photos", ["id", "work_order_id", "photo_url", "created_at"],
       [[q(p["id"]), q(p["wo"]), q(p["url"]), ts(*p["created"])] for p in wo_photos])
w("-- Documentación de cada camión (CAM-127)")
insert("vehicle_documents", ["id", "vehicle_id", "type", "name", "number", "issued_at", "expires_at", "file_url",
                             "replaced_by_id", "active", "created_at"],
       [[q(d["id"]), q(VBY[d["vehicle"]]["id"]), q(d["type"]), q(d["name"]), q(d["number"]),
         dt(d["issued"]) if d["issued"] is not None else "null", dt(d["expires"]) if d["expires"] is not None else "null",
         q(d["file"]), q(d["replaced_by"]), "true", ts(d["created"], 9)] for d in documents])
w("-- Historial de mantenimientos realizados")
insert("maintenance_completions", ["id", "assignment_id", "completed_at", "completed_km", "work_order_id", "notes"],
       [[q(c["id"]), q(c["assignment"]), dt(c["day"]), str(c["km"]), q(c["wo"]), q(c["notes"])] for c in completions])
ADMIN_ID = "(select id from users where username = 'admin')"
w("-- Plantillas de checklist (CAM-150): sus controles ya están copiados en los vehículos de arriba")
insert("checklist_templates", ["id", "name", "created_at"], [[q(t["id"]), q(t["name"]), ts(t["day"] - 2, 9)] for t in TEMPLATES])
insert("checklist_template_items", ["id", "template_id", "label", "type", "section", "position"],
       [[q(i["id"]), q(i["template"]), q(i["label"]), q(i["type"]), q(i["section"]), str(i["position"])] for i in template_items])
w("-- Enganches de remolques (CAM-157)")
insert("trailer_couplings", ["id", "trailer_id", "towing_id", "coupled_at", "uncoupled_at", "coupled_by_name"],
       [[q(c["id"]), q(ALLV[c["trailer"]]["id"]), q(ALLV[c["towing"]]["id"]), ts(c["start"], 6, 30),
         ts(c["end"], 6, 25) if c["end"] is not None else "null", q(c["by"])] for c in couplings])
w("-- Cargas de combustible (CAM-156)")
insert("fuel_loads", ["id", "vehicle_id", "loaded_at", "liters", "amount", "odometer_km", "full_tank", "created_by", "created_at",
                      "alert_liters_per_100_km", "alert_average_liters_per_100_km"],
       [[q(f["id"]), q(ALLV[f["vehicle"]]["id"]), ts(f["day"], f["hh"], f["mm"]), f"{f['liters']:.2f}", f"{f['amount']:.2f}",
         str(f["km"]), "true" if f["full"] else "false", q(DRIVER_IDS[f["by"]]), ts(f["day"], f["hh"], f["mm"]),
         f"{f['alert']:.2f}" if "alert" in f else "null", f"{f['avg']:.2f}" if "avg" in f else "null"] for f in fuel_loads])
w("-- Cubiertas, montajes y mediciones de dibujo (CAM-157)")
insert("tires", ["id", "code", "brand", "model", "size", "purchase_date", "cost", "initial_tread_mm", "status", "retired_at",
                 "retired_reason", "tread_alerted_at", "created_at"],
       [[q(t["id"]), q(t["code"]), q(t["brand"]), q(t["model"]), q(t["size"]), dt(t["purchase"]), f"{t['cost']:.2f}",
         f"{t['initial']:.1f}", q(t["status"]), ts(*t["retired"]) if t["retired"] else "null", q(t["reason"]),
         ts(*t["alerted"]) if t["alerted"] else "null", ts(t["purchase"], 12)] for t in tires])
insert("tire_mountings", ["id", "tire_id", "vehicle_id", "position", "mounted_at", "mounted_km", "removed_at", "removed_km"],
       [[q(m["id"]), q(m["tire"]), q(ALLV[m["vehicle"]]["id"]), q(m["position"].upper().replace("-", "_")),
         ts(m["day"], m["hh"]), str(m["km"]), ts(*m["removed"]) if m["removed"] else "null",
         str(m["removed_km"]) if m["removed"] else "null"] for m in mountings])
insert("tire_tread_readings", ["id", "tire_id", "vehicle_id", "position", "depth_mm", "measured_at", "measured_by",
                               "measured_by_name", "inspection_id"],
       [[q(r["id"]), q(r["tire"]), q(ALLV[r["vehicle"]]["id"]), q(r["position"].upper().replace("-", "_")), f"{r['depth']:.1f}",
         ts(r["day"], r["hh"], r["mm"]), q(DRIVER_IDS[r["by"]]) if r["by"] else ADMIN_ID, q(r["by"] or "admin"),
         q(r["inspection"])] for r in tread_readings])
w("-- Avisos de consumo alto y dibujo bajo, para el admin (los demás los genera el backend)")
insert("notifications", ["id", "recipient_id", "type", "title", "message", "link", "created_at"],
       [["gen_random_uuid()", ADMIN_ID, q(n["type"]), q(n["title"]), q(n["message"]), q(n["link"]), ts(*n["at"])]
        for n in notifications])
w("commit;")
w("")
w("select plate, brand, model, odometer_km from vehicles order by plate;")

with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "seed-demo.sql"), "w", encoding="utf-8", newline="\r\n") as f:
    f.write("\n".join(out) + "\n")

# resumen para verificar
import collections
print("trips", len(trips), "open", sum(t["open"] for t in trips), "inspections", len(inspections), "answers",
      sum(len(i["answers"]) for i in inspections))
print("defects", len(defects), collections.Counter((d["severity"], d["status"]) for d in defects))
print("assignments", len(assignments), "completions", len(completions), "late", late_count)
print("work orders", len(work_orders), collections.Counter(o["status"] for o in work_orders))
print("schedules", len(schedules), collections.Counter(s["status"] for s in schedules))
print("expenses", len(expenses), "total", sum(e["amount"] for e in expenses))
print("documents", len(documents), collections.Counter(d["type"] for d in documents))
print("templates", len(TEMPLATES), "copied items", sum(1 for c in checklist_extras if "created" in c))
print("fuel loads", len(fuel_loads), "partial", sum(not f["full"] for f in fuel_loads), "alerts", [(ALLV[a[0]["vehicle"]]["plate"], a[1], a[2]) for a in FUEL_ALERTS])
print("tires", len(tires), collections.Counter(t["status"] for t in tires), "mountings", len(mountings),
      "readings", len(tread_readings), "couplings", len(couplings))
for v in EXTRA + TRAILERS:
    print(f'{v["plate"]} {v["model"]:<22} km={v["odometer"]:>7} {v["type"]}')
for v in VEHICLES:
    sts = [a["status"] for a in assignments if a["vehicle"] == v["key"]]
    worst = "vencido" if "vencido" in sts else "por_vencer" if "por_vencer" in sts else "al_dia"
    score = max(0, 100 - 25 * sts.count("vencido") - 8 * sts.count("por_vencer"))
    ntrips = sum(1 for t in trips if t["vehicle"] == v["key"])
    ontrip = any(t["open"] for t in trips if t["vehicle"] == v["key"])
    print(f'{v["plate"]} {v["model"]:<22} km={v["odometer"]:>7} {worst:<10} score={score:<3} trips={ntrips} on_trip={ontrip}')
