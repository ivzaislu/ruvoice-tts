#!/usr/bin/env python3
"""Украинская таблица морфологии из VESUM (brown-uk/dict_uk, CC BY-NC-SA 4.0, релиз v6.8.6, dict_corp_vis.txt.bz2) →
morph.bin для паков cis_uk и cis_uk_ext. Формат и биты — как у tools/gdb_morph.py и text/Morph.kt: запись 12 байт,
little-endian, по хэшу FNV-1a 64 от формы строчными, теги: 0–3 часть речи (1 N, 2 A, 4 порядковое, 8 местоимение),
4–6 род (16 м, 32 ж, 64 ср), 7 одушевлённое, 8–13 ед. ч. nom gen dat acc ins loc, 14–19 мн. ч., 20–25 ед. ж. р.,
26–31 ед. ср. р. (у прилагательных).
Берём noun (без местоимений-существительных «він», «себе» — как gdb_morph), adj (с :numr — порядковое, с :pron —
местоимение) и numr — количественные: бита части речи для них нет, только клетки падежей по роду и числу, как у
прилагательного («двома» — мн. ч. ins). Без форм с дефисом, без :prop, :rare, :arch, :slang, :bad; звательный
пропускаем; винительный у прилагательных — только неодушевлённый (:ranim = родительному, его выводит Morph.kt).
Род существительного — по формам ед. ч. леммы, ставится и на формы мн. ч. Апостроф снимаем, как слой.
  vesum_morph.py <dict_corp_vis.txt.bz2> <выход> [--words файл]  — --words: только формы из файла (по слову в строке; тесты).
"""
import argparse, bz2, collections, os, re, struct, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from aot_morph import fnv1a

CASES = ['v_naz', 'v_rod', 'v_dav', 'v_zna', 'v_oru', 'v_mis']
SKIP = {'prop', 'rare', 'arch', 'slang', 'bad'}
GENDER = {'m': 16, 'f': 32, 'n': 64}
ROW = {'m': 8, 'f': 20, 'n': 26, 'p': 14}
APOS = re.compile("[’'`´ʼ‘]")
# самопроверка: форма → ожидаемые теги (сверено глазами с dict_corp_vis v6.8.6)
CHECK = {
    'кілометрів': 1 | 16 | 1 << 15,                                   # сущ. м. р., род. мн.
    'гривні': 1 | 32 | 1 << 9 | 1 << 10 | 1 << 13 | 1 << 14 | 1 << 17,  # ж. р.: род./дат./местн. ед., им./вин. мн.
    "п'ятьма": 1 << 18,                                               # количественное, тв. мн.
    'першого': 1 | 64 | 4 | 1 << 9 | 1 << 27,                         # «перше» (сущ. ср. р.) род. ед.; порядковое м./ср. род.
    'книжки': 1 | 32 | 1 << 9 | 1 << 14 | 1 << 17,                    # род. ед., им./вин. мн.
    'столом': 1 | 16 | 1 << 12,                                       # тв. ед.
    'вода': 1 | 32 | 1 << 8,                                          # им. ед.
    'двома': 1 << 18,                                                 # количественное, тв. мн.
    'тисячами': 1 | 32 | 1 << 18,                                     # сущ. ж. р. (:numr), тв. мн.
    'новій': 2 | 1 << 22 | 1 << 25,                                   # прил. ж. р. дат./местн.
}


def bits(tag, gender):
    """Теги одной строки VESUM; gender — роды леммы существительного; 0 — форма не нужна."""
    p = tag.split(':')
    flags = set(p)
    case = next((c for c in p if c.startswith('v_')), None)
    num = next((c for c in p if c in ROW), None)
    if SKIP & flags or case not in CASES or num is None: return 0
    cell = 1 << ROW[num] + CASES.index(case)
    if p[0] == 'noun':
        if 'pron' in flags: return 0
        return 1 | gender | (128 if 'anim' in flags else 0) | (1 << (14 if num == 'p' else 8) + CASES.index(case))
    if p[0] == 'adj':
        if case == 'v_zna' and 'ranim' in flags: return 0
        return (4 if 'numr' in flags else 8 if 'pron' in flags else 2) | cell
    return cell if p[0] == 'numr' else 0


def blocks(path):
    """Леммы VESUM: строка леммы без отступа, формы — с отступом; (форма, тег) без комментария."""
    block = []
    with bz2.open(path, 'rt', encoding='utf-8') as f:
        for line in f:
            if not line.startswith(' ') and block: yield block; block = []
            w, tag = line.split('#')[0].split()
            block.append((w, tag))
    if block: yield block


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('src'); ap.add_argument('out'); ap.add_argument('--words')
    a = ap.parse_args()
    only = {APOS.sub('', w.strip().lower()) for w in open(a.words, encoding='utf-8') if w.strip()} if a.words else None
    table = collections.defaultdict(int)
    for block in blocks(a.src):
        gender = 0
        for _, tag in block:
            p = tag.split(':')
            if p[0] == 'noun': gender |= GENDER.get(p[2] if len(p) > 2 else '', 0)
        for w, tag in block:
            if '-' in w: continue
            w = APOS.sub('', w.lower())
            if only is not None and w not in only: continue
            t = bits(tag, gender)
            if t: table[w] |= t
    rows = {}
    for w, t in table.items():
        h = fnv1a(w)
        rows[h] = rows.get(h, 0) | t
    signed = sorted((h - (1 << 64) if h >> 63 else h, t) for h, t in rows.items())
    with open(a.out, 'wb') as out:
        for h, t in signed: out.write(struct.pack('<qI', h, t))
    for w, t in CHECK.items():
        w = APOS.sub('', w)
        if only is None or w in only: assert table[w] == t, f'{w}: {table[w]:#x} != {t:#x}'
    print(f'форм: {len(rows)}, байт: {len(rows) * 12}, существительных: {sum(1 for t in table.values() if t & 1)} → {a.out}')


if __name__ == '__main__':
    main()
