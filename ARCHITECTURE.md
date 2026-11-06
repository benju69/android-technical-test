# Architecture & choix techniques

> Document de justification des choix d'architecture, des patterns et des librairies
> appliqués (exigence de l'énoncé)

Application : **AndroidRecruitmentTestApp** : liste d'albums (photos), favoris, écran de détail, persistance offline.

Stack : Kotlin 2.4, Jetpack Compose, AGP 9.5 / Gradle 9.8, module `:app` +
module `:data`.

---

## Sommaire

1. [Injection de dépendances : Hilt](#1-injection-de-dépendances--hilt)
2. [Architecture par couches (MVVM + Repository)](#2-architecture-par-couches) — *à venir*
3. [Persistance offline : Room](#3-persistance-offline--room) — *à venir*
4. [Navigation](#4-navigation) — *à venir*
5. [Réseau : Retrofit + kotlinx-serialization](#5-réseau) — *à venir*
6. [UI : Compose + Spark design system](#6-ui) — *à venir*
7. [Tests](#7-tests) — *à venir*
8. [Bugs trouvés & corrigés](#8-bugs-trouvés--corrigés) — *à venir*
9. [Planification des évolutions](#9-planification-des-évolutions) — *à venir*

---

## 1. Injection de dépendances : Hilt

### 1.1 Pourquoi un framework d'injection (le constat de départ)

Le projet d'origine n'avait **aucun framework** : la DI était manuelle, portée par un
`Application` qui implémentait une interface maison (`AppDependenciesProvider`) et
exposait un sac d'objets (`AppDependencies`) où chaque dépendance était résolue par
`by lazy` au moment du premier accès, puis descendue à la main :
`activity → application → AppDependencies → DataDependencies → AlbumRepository`.

Cette approche fonctionne à l'échelle de 3 classes, mais elle casse dès qu'elle
grandit :

| Problème de la DI manuelle | Symptôme visible dans le code d'origine |
|---|---|
| **Câblage manuel dupliqué** | `MainActivity` et `DetailsActivity` récupéraient chacun `dependencies…` et recopiaient le chemin d'acquisition |
| **Aucun contrôle de portée** | `AnalyticsHelper` était créé en singleton "de facto" mais `initialize(context)` recevait un Context d'**Activity**, stocké dans un objet vivant avec l'Application → **fuite mémoire** (LeakCanary) |
| **Initialisation ordonnée à la main** | `AnalyticsHelper` devait être `initialize()`-é *avant usage* dans chaque écran, sinon silent no-op (le helper ignorait tout tracking si non initialisé) |
| **Testabilité** | Pour construire un `AlbumsViewModel` il fallait recréer toute la chaîne à la main dans le test |
| **Coûts d'incrémentation** | Ajouter une dépendance = naviguer dans tout le graphe manuel : exactement le genre de dette qu'une code review rigoureuse finit par interdire |

### 1.2 Pourquoi **Hilt** spécifiquement

Le choix a été fait parmi : DI manuelle (statu quo), Koin, Dagger 2 nu, Hilt.

**Hilt = Dagger 2 au-dessus d'une API standardisée Android.** Ce qui l'a emporté :

1. **Sécurité à la compilation.** Le graphe complet (et si chaque dépendance est
   fournie, au bon scope, dans le bon composant) est vérifié par le processeur
   d'annotations. Un lien manquant = **erreur de build**, jamais un crash à
   l'exécution — contrairement à Koin (résolution à l'exécution), où un binding
   oublié ne se voit qu'au lancement de l'app. Pour le critère d'énoncé
   *« l'application ne doit pas crasher systématiquement »*, une erreur à la
   compilation plutôt qu'à l'exécution est le meilleur pari.
2. **Dagger lui-même est ce qui est derrière la majorité des apps professionnelles**
   du marché Android — Google écrit son guide de référence autour de Hilt. Le
   transfert de compétences est direct pour toute équipe Android.
3. **Intégration Android-native** que Dagger nu n'apporte pas :
   `@HiltAndroidApp`, `@AndroidEntryPoint` pour les Activities/Fragments,
   `@HiltViewModel` compatible avec `viewModelScope` + `SavedStateHandle`
   automatique, `@ApplicationContext` typé qui rend la fuite de Context
   *structurellement impossible* (le qualifier porte le bon contexte).
4. **KSP (pas kapt)** : Hilt 2.60+ supporte officiellement KSP stable, qui est
   déjà dans ce projet pour la compilation (2.3.10) — pas de kapt, pas
   d'incompatibilité de build, et les générations sont nettement plus rapides.
5. **Koin** reste un bon choix sur des projets légers, mais ici il aurait exigé de
   tenir le graphe "à la main" en Kotlin, sans vérification compile-time ; à
   l'inverse, le boilerplate Hilt est quasi nul au regard des 4 bindings voulus
   (`Json`, `OkHttpClient`, `Retrofit`, `AlbumApiService`).

### 1.3 Comment c'est connecté

Tout vit dans le composant **`SingletonComponent`** (portée app-wide) :
`@HiltAndroidApp` sur `PhotoApp` installe le graphe, `:data` expose son module
réseau `DataModule` (`@Module @InstallIn(SingletonComponent)`) qui fournit
`Json`, `OkHttpClient` (log BODY en debug only), `Retrofit` et
`AlbumApiService`. `AlbumRepository` et `AnalyticsHelper` sont, eux, résolus
par **injection de constructeur** (`@Inject constructor`) : pas besoin de
`@Provides` "à la main", Hilt construit la classe dès que toutes ses
dépendances sont dans le graphe — la notation standard pour ce genre d'objet.
La description détaillée du module `:data` viendra avec sa refonte
(§2 Architecture par couches).

#### Côté consommateurs

- **Activities** → `@AndroidEntryPoint` ajoute le code de bytecode par plugin
  Gradle pour que la field injection se fasse proprement (avant `onCreate`) :

  ```kotlin
  @AndroidEntryPoint
  class MainActivity : ComponentActivity() {
      @Inject lateinit var analyticsHelper: AnalyticsHelper
  }
  ```

- **ViewModels** → `@HiltViewModel` + `viewModels()` : plus aucune
  `ViewModelProvider.Factory` à écrire, Hilt fournit à la fois le
  graphe et le store de ViewModels dans le bon scope :

  ```kotlin
  @HiltViewModel
  class AlbumsViewModel @Inject constructor(
      private val repository: AlbumRepository,
  ) : ViewModel()
  ```

- **Module library**: `:data` expose son graphe via annotations **sans appliquer
  le plugin Gradle Hilt** (réservé au transform de bytecode pour les
  `@AndroidEntryPoint`, qui ne concerne pas une library). Seuls la dépendance
  d'annotations (`hilt-android`) et le `ksp(hilt-compiler)` suffisent.

### 1.4 Conséquences concrètes (avant / après)

| | Avant (manual DI) | Après (Hilt) |
|---|---|---|
| Fuite de Context | `AnalyticsHelper.initialize(activity)` stockait l'Activity dans un objet singleton "de facto" | `@ApplicationContext` injecté : le type garantit un Context app, plus aucun appel à `initialize()` n'existe |
| Initialisation à la main | `initialize()` requis dans chaque Activity avant usage, no-op silencieux sinon | `AnalyticsHelper` injecté déjà prêt à l'emploi |
| Factory ViewModel | `AlbumsViewModel.Factory` maison + `ViewModelProvider(this, factory)` | `by viewModels()`, sans factory |
| Ajouter une dépendance | Ajouter au sac `AppDependencies` + traverser chaque Activity | Ajouter le binding (souvent juste `@Inject constructor`) et l'injecter |
| Vérification | Aucune — erreurs possibles à l'exécution | Erreurs découvertes par le compilateur (le graphe complet est vérifié) |
| Test unitaire VM | Chaîne recréée à la main dans le test | Le constructeur reste une fonction ordinaire, `AlbumsViewModel(repository)` : test unitaire inchangé, non attaché à Hilt |

### 1.5 Ce que ce choix *ne* fait pas (franchise sur les limites)

- Hilt impose des traitements de bytecode (plugin Gradle) et une
  structure par composants, plus rigide que Koin sur des projets très petits.
- Les ViewModels `@HiltViewModel` restent **testables en test unitaire JVM pur
  comme prévu** (on les construit à la main avec leurs arguments); en contrepartie
  il y aura, plus tard, des tests **Robolectric/instrumentés** si l'on veut tester
  le graphe lui-même.
