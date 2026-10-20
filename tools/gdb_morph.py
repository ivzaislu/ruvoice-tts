#!/usr/bin/env python3
"""Белорусская таблица морфологии из GrammarDB (CC BY-SA 4.0, github.com/Belarus/GrammarDB) → morph.bin для пака cis_be.
Формат и биты — как у русской (tools/aot_morph.py, text/Morph.kt): запись 12 байт, little-endian, по хэшу FNV-1a 64
от формы строчными («ё»→«е»), теги: 0–3 часть речи (1 N, 2 A, 4 порядковое, 8 местоимение), 4–6 род (16 м, 32 ж, 64 ср),
7 одушевлённое, 8–13 ед. ч. nom gen dat acc ins loc, 14–19 мн. ч., 20–25 ед. ж. р., 26–31 ед. ср. р. (у прилагательных).
Вход — каталог распакованного релиза (N1–N3.xml, A1–A2.xml, M.xml, P.xml), только варианты правапісу 2008. Имена
собственные (NP.xml) и формы с дефисом не берём; апостроф снимаем — BelNormalizer снимает его до разбора.
  gdb_morph.py <каталог релиза> <выход> [--words файл]  — --words: только формы из файла (по слову в строке; тесты).
"""
import argparse, collections, os, re, struct, sys, xml.etree.ElementTree as ET
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from aot_morph import fnv1a

CASES = 'NGDAIL'
APOS = re.compile("[’'`´ʼ‘+]")


def form_text(t):
    return APOS.sub('', t).lower()


def noun_bits(ptag, ftag):
    """Существительное: тег парадигмы NCIINM1 (3-я — одушевлённость, 6-я — род), формы NS/GP… или MNS… у субстантивов."""
    g = ptag[5] if len(ptag) > 5 else ''
    if len(ftag) == 3:            # субстантивированное прилагательное: род в теге формы
        g, ftag = ftag[0], ftag[1:]
    if len(ftag) != 2 or ftag[0] not in CASES or ftag[1] not in 'SP': return 0
    t = 1 | {'M': 16, 'F': 32, 'N': 64}.get(g, 0) | (128 if ptag[2:3] == 'A' else 0)
    return t | 1 << ((8 if ftag[1] == 'S' else 14) + CASES.index(ftag[0]))


def adj_bits(pos, ftag):
    """Прилагательное, порядковое, местоимение: формы MNS, FGS, NNS, PNP."""
    if len(ftag) != 3 or ftag[1] not in CASES or ftag[2] not in 'SP': return 0
    row = 14 if ftag[0] == 'P' else {'M': 8, 'F': 20, 'N': 26}.get(ftag[0])
    if row is None: return 0
    return pos | 1 << (row + CASES.index(ftag[1]))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('src'); ap.add_argument('out'); ap.add_argument('--words')
    a = ap.parse_args()
    only = {w.strip().lower() for w in open(a.words, encoding='utf-8') if w.strip()} if a.words else None
    table = collections.defaultdict(int)
    for name in ('N1.xml', 'N2.xml', 'N3.xml', 'A1.xml', 'A2.xml', 'M.xml', 'P.xml'):
        for _, el in ET.iterparse(os.path.join(a.src, name)):
            if el.tag != 'Paradigm': continue
            ptag = el.get('tag') or ''
            if name.startswith('N'): kind = 'N'
            elif name.startswith('A'): kind = 2
            elif name == 'M.xml': kind = 4 if ptag.startswith('MAO') else None
            else: kind = 8 if ptag[1:2] == 'A' else None
            if kind is not None and ptag[:2] != 'NP':
                for v in el.iter('Variant'):
                    if 'A2008' not in (v.get('pravapis') or ''): continue
                    for f in v.iter('Form'):
                        if not f.text or '-' in f.text or f.get('options') == 'anim': continue
                        w = form_text(f.text)
                        if only is not None and w not in only: continue
                        t = noun_bits(ptag, f.get('tag') or '') if kind == 'N' else adj_bits(kind, f.get('tag') or '')
                        if t: table[w] |= t
            el.clear()
    rows = {}
    for w, t in table.items():
        h = fnv1a(w.replace('ё', 'е'))
        rows[h] = rows.get(h, 0) | t
    signed = sorted((h - (1 << 64) if h >> 63 else h, t) for h, t in rows.items())
    with open(a.out, 'wb') as out:
        for h, t in signed: out.write(struct.pack('<qI', h, t))
    print(f'форм: {len(rows)}, байт: {len(rows) * 12}, существительных: {sum(1 for t in table.values() if t & 1)} → {a.out}')


if __name__ == '__main__':
    main()
