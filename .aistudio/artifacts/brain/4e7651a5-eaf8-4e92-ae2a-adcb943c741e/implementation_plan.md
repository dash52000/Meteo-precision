# Météo Précision — Mise à Jour Ergonomie S25, Icônes Météo & Sélecteurs d'Heures

Amélioration ciblée de la lisibilité et de l'ergonomie sur Samsung Galaxy S25 : ajout d'icônes météo explicites (soleil, éclaircies, nuageux, brouillard, pluie, neige, orage), compactage du Top 3 des créneaux course en lignes simples ultra-fines, et remplacement des barres (`Slider`) par des sélecteurs d'heures directs (`-` / `+`) pour la configuration des trajets du lieu « Boulot ».

## Revue Utilisateur & Décisions Clés

> [!IMPORTANT]
> Voici la synthèse de vos préférences confirmées pour cette mise à jour ergonomique :

- **Décision Confirmée — Icônes Météo (Codes WMO Open-Meteo)** : Ajout d'icônes météo visuelles multi-couleurs (Soleil, Soleil voilé / nuageux, Couvert, Brouillard, Pluie / Averses, Neige, Orage) accompagnées du libellé du temps en français dans l'en-tête principal, l'inspecteur 24 h, le bloc Week-end et les barres 7 jours.
- **Décision Confirmée — Top 3 Créneaux Course Ultra-Compact** : Remplacement des grosses tuiles à double ligne par une **liste ultra-compacte d'une seule ligne par créneau** (hauteur réduite, lecture tabulaire directe : rang, créneau horaire, pastille couleur, ressenti, pluie/vent et motif principal).
- **Décision Confirmée — Configuration des Heures « Boulot »** : Suppression des barres `Slider` qui s'affichaient mal dans la fenêtre d'édition de lieu ; remplacement par des **sélecteurs d'heures à boutons `-` et `+` rapides** pour régler précisément l'heure de début et l'heure de fin du trajet Aller et du trajet Retour, au sein d'un formulaire défilable sans coupure.

---

## 1. Vue d'Ensemble & Concept Fondamental

- **Ce que fait cette mise à jour** :
  1. Traduit les codes météo numériques WMO (`weather_code` d'Open-Meteo) en **icônes graphiques expressives** (soleil doré, soleil + nuage pour éclaircies, nuages superposés, nappes de brouillard, gouttes de pluie cyan, éclair d'orage) pour identifier l'état du ciel d'un seul coup d'œil tout en conservant la précision des chiffres.
  2. Allège l'encombrement vertical de la section **Score Course** en présentant les 3 meilleurs créneaux sous forme de 3 lignes fines et alignées.
  3. Rend l'édition d'un lieu de type **Boulot** fluide sur l'écran du Galaxy S25 grâce à un conteneur scrollable et des boutons `-` / `+` dédiés à chaque borne horaire.

---

## 2. Expérience Utilisateur & Design Visuel

### 1. Icônes Météo Vectorielles sur Mesure (WMO `weather_code`)
- Création d'un composant dédié `WeatherConditionIcon` dessinant des icônes météo nettes et contrastées (avec variante Jour / Nuit) :
  - **Code 0 (Ciel dégagé)** : Soleil éclatant ambre/or (ou croissant de lune clair la nuit).
  - **Codes 1, 2 (Soleil nuageux / Éclaircies)** : Soleil partiellement masqué par un nuage clair (« soleil nuageux »).
  - **Code 3 (Couvert / Nuageux)** : Double nuage dense gris-bleuté.
  - **Codes 45, 48 (Brouillard / Brouillard givrant)** : Nuage avec trois lignes horizontales de brume.
  - **Codes 51–67, 80–82 (Bruine, Pluie, Averses)** : Nuage sombre avec traits de pluie cyan inclinés.
  - **Codes 71–77, 85–86 (Neige)** : Nuage avec flocons blancs/cyan.
  - **Codes 95, 96, 99 (Orage)** : Nuage d'orage avec éclair jaune/rouge.
- **Emplacements d'affichage** :
  - **En-tête principal** : Grande icône météo à gauche de la température actuelle + libellé en français (ex. *« Éclaircies »*, *« Brouillard »*, *« Pluie faible »*).
  - **Graphe 24 h (curseur)** : Icône météo + libellé de l'heure inspectée.
  - **Bande Score Course (6 h – 21 h)** : Petite icône météo intégrée dans chaque colonne horaire.
  - **Focus Week-end & Prévisions 7 Jours** : Icône météo du jour affichée sur chaque carte Samedi/Dimanche et sur chaque ligne des 7 jours.

### 2. Liste Ultra-Compacte du « Top 3 Créneaux Course »
- Chaque créneau recommandé est affiché sur **une seule ligne horizontale compacte** (`padding` vertical de `5.dp`) :
  - Gauche : Badge `#1` + créneau `07h–08h` + point lumineux Vert/Orange/Rouge.
  - Centre : Motif synthétique sur une ligne (ex. `Idéal` ou `Rafales 35 km/h`).
  - Droite : Données chiffrées alignées (`13°C · 0,0mm · 18km/h`).

### 3. Sélecteur d'Heures `-` / `+` pour le Lieu « Boulot »
- Dans `LocationEditorDialog` :
  - Le corps de la fenêtre devient défilable (`verticalScroll`) pour que toutes les options soient accessibles confortablement sur le Galaxy S25.
  - Lorsque le type **Boulot** est sélectionné, affichage de deux lignes claires (**Trajet Aller** et **Trajet Retour**) comportant chacune deux sélecteurs horaires (`Début` et `Fin`) avec boutons `-` et `+` encadrant l'heure affichée en grand (`07h`, `09h`, `17h`, `19h`).

---

## 3. Décisions Produit & Compromis Techniques

- **Icônes dessinées sur Canvas (`WeatherConditionIcon`) plutôt qu'icônes génériques** :
  - *Pourquoi* : Les icônes standards Material ne proposent pas de combinaison bicolore précise comme « soleil nuageux » (soleil doré + nuage blanc) ou « brouillard » lisible sur fond sombre. Un dessin vectoriel Canvas garantit la distinction visuelle immédiate entre grand soleil, soleil nuageux, nuageux, brouillard, pluie et orage à n'importe quelle taille (`16.dp` à `42.dp`).
- **Sélecteurs incrémentaux `-` / `+` bornés (`0h` à `23h`)** :
  - *Pourquoi* : Contrairement aux barres `Slider` qui sont difficiles à manipuler dans une boîte de dialogue étroite et consomment beaucoup de hauteur, les boutons `-` et `+` offrent un contrôle exact à l'heure près avec une cible tactile confortable et maintiennent automatiquement la cohérence (`Fin ≥ Début + 1h`).

---

## 4. Architecture Technique & Stratégie de Données

### Diagramme des Composants Mis à Jour

```
┌──────────────────────────────────────────────────────────────────────────┐
│                     COMPOSANTS UI MIS À JOUR (S25)                       │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ WeatherConditionIcon (Nouveau composant vectoriel WMO)             │  │
│  │  - wmoCodeToLabelFr(code) : "Ensoleillé", "Soleil nuageux",        │  │
│  │    "Nuageux", "Brouillard", "Pluie", "Orage"...                    │  │
│  │  - Rendu bicolore Jour/Nuit : Soleil, Soleil+Nuage, Brouillard...  │  │
│  └───────────────┬──────────────────────────────────┬─────────────────┘  │
│                  │                                  │                    │
│                  ▼                                  ▼                    │
│  ┌───────────────────────────────────┐  ┌─────────────────────────────┐  │
│  │ DashboardScreen & WeatherCharts   │  │ LocationsScreen             │  │
│  │  - En-tête : Icône + Libellé WMO  │  │  - LocationEditorDialog     │  │
│  │  - 24h & 7j & Week-end : Icônes   │  │    scrollable sur S25       │  │
│  │  - Top 3 Créneaux : 1 seule ligne │  │  - HourStepperPicker (- / +)│  │
│  │    ultra-compacte par créneau     │  │    Aller (Début / Fin) &    │  │
│  └───────────────────────────────────┘  │    Retour (Début / Fin)     │  │
│                                         └─────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
```

### Correspondance Composants Interactifs & Transitions d'État
- **`WeatherConditionIcon(weatherCode, isDay, size)`** : Utilisé dans `TelemetryHeaderCard`, `Hourly24hMultiCurveChart`, `RunningScoreSection`, `WeekendHighlightBlock` et `Daily7DaysBarsSection`.
- **`CompactTop3RunningSlotRow`** : Remplace l'ancien bloc volumineux dans `RunningScoreSection` ; conserve le clic pour ouvrir la boîte de dialogue de détail horaire.
- **`HourStepperRow` dans `LocationEditorDialog`** : Deux contrôles `[-][ 07h ][+]` et `[-][ 09h ][+]` par trajet (Aller et Retour), mettant à jour l'état local et garantissant `0 <= start < end <= 23`.
