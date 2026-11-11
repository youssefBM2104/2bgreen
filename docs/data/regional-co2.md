# Regional CO2 estimate (G7)

The regional datasets (`eco2mix-regional-tr`, `eco2mix-regional-cons-def`) have no `taux_co2`. `grid-intelligence`
estimates it from the production mix of each reading. The rule is the domain class `RegionalCo2Estimator`; the
regional poll applies it to every reading before publishing it, so Kafka, `speed_snapshot`, the read API (K2), the
band of the stream (G9) and the rule engine of `platform` all see the same value. Every estimated value has
`estimated: true`.

## Decision

**Same definition as RTE's national value: direct emissions of combustion divided by the total production of the
region, imports and exports not counted.**

```
co2Intensity = (429 × thermal + 494 × bioenergy) / (nuclear + thermal + wind + solar + hydro + bioenergy)
```

MW in, gCO2eq/kWh out, rounded to a whole number (RTE gives the national value as a whole number too). Nuclear, wind,
solar and hydro emit nothing at combustion, so they only count in the total. Pumping and batteries are not in the
total, as for RTE (checked below).

Why this definition: national and regional values are then the **same quantity**. A regional figure can be compared
with the national one and with the limits of the traffic light (20 and 40, F5 and G9), and the regions, weighted by
production, roughly add up to France (not exactly: see "The `thermal` aggregate").

## Emission factors and their source

| Source | gCO2eq/kWh | Used for regions |
|---|---|---|
| Gas | 429 | yes, for the whole `thermal` aggregate (see below) |
| Oil | 777 | no (regional data has no split) |
| Coal | 986 | no (same) |
| Bioenergy | 494 | yes |
| Nuclear, hydro, wind, solar | 0 | yes |

These are RTE's factors for éCO2mix. RTE's methodology page
([eCO2 emissions](https://www.rte-france.com/en/data-publications/eco2mix/eco2-emissions)) says how they are built:
the "primary" emission factor of each fuel from the ADEME Base Carbone, divided by the average efficiency of the
plants (RTE's assumptions, ENTSO-E guidelines). It also says the indicator "reflects solely the greenhouse gases
produced by combustion operations in thermal power plants in France" (no construction, no fuel extraction) and that it
does "not include energy exchanges at interconnections". The page does not print the numbers.

**Checked on our own data instead of trusted.** A least-squares fit of `taux_co2 × total production` on the four
emitting sources, over the 5,653 national readings of `speed_snapshot` (2026-09-01 to 2026-10-29), gives gas **428**,
oil **781**, bioenergy **492**: the factors above. With them, the formula finds RTE's value with a mean error of
0.26 g and never more than 0.8 g, which is the rounding of RTE's integer. Coal cannot be fitted on that period (no coal
ran); 986 is the value commonly published for it. Dividing by consumption instead of production is off by 4.8 g on
average: the denominator is production.

**Rejected: ADEME life-cycle factors** (Base Empreinte, figures reported for instance as 43.9 g for solar, 420 for
gas, 1060 for coal; not read from the base itself). They measure another quantity: they add the building of the plants
and the fuel chain, so nuclear, wind and solar no longer count 0. The national value we show is RTE's combustion-only
figure, so regional values on another basis could not be compared with it, nor with the traffic-light limits.

## The `thermal` aggregate

Regional data gives fossil production as one figure, `thermique`, without the gas / coal / oil split. It gets the
factor of gas.

- Over 2026-09-01 to 2026-10-29, French fossil production was **97.1 % gas**, 2.9 % oil, 0 % coal.
- Applied to the national readings with gas, coal and oil put together like regional data, the formula gives a mean
  error of 0.41 g against RTE's value, 96 % of readings within 1 g, at worst 7.1 g (a peak of oil).
- **Rejected: the national fossil mix of the same quarter-hour** as factor (472 g on 2026-11-10 12:00, with coal
  running). It would make the regions add up to France, but needs the national snapshot of the same time in the regional
  poll (a dependency between scopes, and a result that depends on which poll ran first), and it would spread the coal of
  the two regions that have coal plants over all twelve.

## Missing values

- Every one of the six sources must be known. A missing one would change the total and give a wrong intensity, so
  there is **no estimate**: `co2Intensity` stays `null` and `estimated` stays `false`. Missing is never 0.
- A region that produces nothing (total 0) has no estimate either.
- National readings are never estimated: they keep RTE's value, or nothing.
- A regional reading that already has a CO2 value keeps it.

## Rejected: counting imports

The estimate describes what a region **produces**, not what its consumers draw from the grid. For regions that import
most of their electricity the two differ a lot. One live sample, 2026-11-10 12:00 UTC (national `taux_co2` 59):

| Region | Consumption MW | Production MW | Production-based (chosen) | Imports at national intensity |
|---|---|---|---|---|
| Île-de-France | 10,359 | 984 | 411 | 93 |
| Bretagne | 2,999 | 973 | 216 | 110 |
| Provence-Alpes-Côte d'Azur | 4,559 | 3,742 | 181 | 159 |
| Hauts-de-France | 6,837 | 5,652 | 145 | 130 |
| Pays de la Loire | 3,769 | 749 | 142 | 75 |
| Grand Est | 5,785 | 8,839 | 90 | 90 |
| Bourgogne-Franche-Comté | 2,538 | 823 | 23 | 47 |
| Occitanie | 4,456 | 4,426 | 15 | 16 |
| Auvergne-Rhône-Alpes | 7,497 | 15,088 | 15 | 15 |
| Normandie | 3,593 | 8,432 | 11 | 11 |
| Nouvelle-Aquitaine | 4,361 | 8,452 | 7 | 7 |
| Centre-Val de Loire | 2,363 | 8,902 | 2 | 2 |

The alternative, `(local emissions + net imports × national intensity) / (production + net imports)`, was set aside:

- it needs the national snapshot of the same quarter-hour in the regional computation (same objection as above);
- it is still crude: the imports of Île-de-France come mostly from the nuclear of Centre-Val de Loire and Normandie, not
  from an average France, so 93 g is too high too. Doing it properly needs the flows between regions, which ODRÉ does
  not give (`ech_physiques` is one net figure per region);
- it is not what RTE computes, so national and regional would no longer be the same quantity.

## Limits (to say wherever the value is shown)

1. **Production, not consumption.** Île-de-France, Bretagne, Pays de la Loire and Provence-Alpes-Côte d'Azur look
   dirty because the little they produce is mostly gas, waste and biomass; most of what they consume is imported and
   low-carbon. For the regional official (U15) this is the right figure (the transition of what the region produces).
   For a citizen of such a region it is not "the CO2 of my plug": the frontend must say so, or show the national value.
2. **Coal and oil are counted as gas.** When they run, regions with coal or oil plants are underestimated. On the sample
   above, with 547 MW of coal in France, the production-weighted mean of the twelve regions is 51.9 g against 59 for
   RTE's national value (12 % under). In autumn 2026 it is 0.4 g on average; in a cold winter it will be more.
   **To re-check in winter:** compare the production-weighted mean of the regions with the national value on a few cold
   days.
3. **Constant factors.** No plant efficiency per region or per hour, no life cycle, no batteries.
4. **Readings stored before G7 have no CO2.** The poller only reads back 3 hours. To fill a dev database: stop the
   poller, `DELETE FROM grid.speed_snapshot WHERE scope LIKE 'region:%'`, start it again: the regional backfill runs
   again (about 600 ODRÉ calls, once).
5. The 30-min periods of the merged view average the quarter-hour estimates (G4), as for every other value.

## Where it is computed, and why there

- **Not in the ACL.** The ACL translates ODRÉ into the domain and invents nothing; the estimate is a business rule of
  ours, with its own choices (factors, definition). `RegionalRealtimeMapping` still gives `co2Intensity: null` and
  `estimated: false`.
- **In the domain** (`domain.RegionalCo2Estimator`, pure: no Spring, no ODRÉ type), **called by the regional poll**
  (`application.PollRegionalReadings`) before publishing. The value then travels in the K1 payload: the stream stores
  it, the band step (G9) uses it, `platform` reads it from K2. Computing it at read time instead would leave Kafka
  consumers without it.
- The regional batch (G6) will call the same rule on consolidated readings.
