#!/usr/bin/env python3
"""Кандидаты украинских ударений «слово сл+ово» для stress.txt паков cis_uk и cis_uk_ext (export_pack.py --cand).
Основа — формы английского Викисловаря из kaikki.org (https://kaikki.org/dictionary/Ukrainian/, CC BY-SA 4.0):
ударение U+0301 после гласной → «+» перед ней, побочное U+0300 снимаем, апостроф снимаем (слой ищет слово без него),
нижний регистр; формы с пробелом не берём. Слово, у которого в kaikki два разных ударения, отбрасываем — омографы
одним ударением не фиксируем. Добавка — eugenysilin/russian-ukrainian-stress-data uk-stress-dict.gz (CC BY-SA 4.0;
строка «слово<TAB>позиция сразу после ударной»): омографы там схлопнуты в одно чтение, поэтому только слова, которых
нет в kaikki и нет в heteronyms.tsv (lang-uk/ukrainian-heteronyms-dictionary; только фильтр, в пак не идёт).
Нормализация — normalize() из export_pack: служебные слова без «+», ровно один «+» в размечаемой части.
  uk_stress.py --kaikki F.jsonl --eugenysilin uk-stress-dict.gz --heteronyms heteronyms.tsv -o stress_cand.txt
Оценка на бенчмарке lang-uk (lexical_stress_dataset.csv, «+» после ударной): доля верных ударений в словах с 2+
гласными у акцентора и у акцентора + словаря (кандидаты или stress.txt пака), отдельно омографы по heteronyms.tsv:
  uk_stress.py --eval stress.txt --bench lexical_stress_dataset.csv --heteronyms heteronyms.tsv
"""
import argparse, csv, gzip, json, os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from export_pack import APOS, NO_MARK, VOWELS, markable, normalize, read_cand

LETTERS = set('абвгґдеєжзиіїйклмнопрстуфхцчшщьюя-')
V = VOWELS['uk']


def bare(s):
    for a in APOS: s = s.replace(a, '')
    return s.lower()


def kaikki(path):
    """Слово → множество вариантов ударения (None — форма, которую normalize не принял)."""
    variants = {}
    with open(path, encoding='utf-8') as f:
        for line in f:
            for fm in json.loads(line).get('forms', ()):
                s = fm['form']
                if '́' not in s or ' ' in s: continue
                out = []
                for ch in bare(s).replace('̀', ''):
                    if ch == '́':
                        if out: out.insert(len(out) - 1, '+')
                    else:
                        out.append(ch)
                v = ''.join(out); w = v.replace('+', '')
                if not w or not set(w) <= LETTERS or w[0] == '-' or w[-1] == '-': continue
                variants.setdefault(w, set()).add(normalize(v, 'uk'))
    return variants


def heteronyms(path):
    with open(path, encoding='utf-8') as f:
        return {bare(line.split('\t')[0]) for line in f if line.strip()}


def build(a):
    var = kaikki(a.kaikki)
    cand = {w: next(iter(vs)) for w, vs in var.items() if len(vs) == 1 and None not in vs}
    two = sum(1 for vs in var.values() if len(vs - {None}) > 1)
    het = heteronyms(a.heteronyms)
    n_k, e = len(cand), dict(eug=0, kaikki=0, het=0, letters=0, normalize=0)
    with gzip.open(a.eugenysilin, 'rt', encoding='utf-8') as f:
        for line in f:
            w, pos = line.split('\t'); pos = int(pos) - 1
            v = bare(w[:pos] + '+' + w[pos:]); w = v.replace('+', '')
            k = ('kaikki' if w in var else 'het' if w in het else
                 'letters' if not set(w) <= LETTERS or w[0] == '-' or w[-1] == '-' else 'normalize' if not normalize(v, 'uk') else 'eug')
            e[k] += 1
            if k == 'eug': cand[w] = normalize(v, 'uk')
    with open(a.o, 'w', encoding='utf-8', newline='\n') as f:
        f.write('# кандидаты ударений: Викисловарь (kaikki.org) и eugenysilin/russian-ukrainian-stress-data, CC BY-SA 4.0\n')
        f.writelines(f'{w} {v}\n' for w, v in sorted(cand.items()))
    # normalize даёт None служебному и односложному слову (им «+» и не нужен) — это не потеря; потеря — слово с
    # размечаемой частью: два «+» в части, нет «+» в размечаемой части («Ко́ста-Ри́ка»: ударение на обеих частях
    # не у всех форм), «+» не на гласной
    lost = [w for w, vs in var.items() if None in vs and any(markable(p, 'uk') for p in w.split('-'))]
    print(f'{a.o}: {len(cand)} слов; kaikki {len(var)}: взято {n_k}, два ударения {two}, потеряны normalize {len(lost)} '
          f'(с дефисом {sum("-" in w for w in lost)}); eugenysilin: взято {e["eug"]}, есть в kaikki {e["kaikki"]}, '
          f'омографы {e["het"]}, не те буквы {e["letters"]}, потеряны normalize {e["normalize"]}; '
          f'в кандидатах с дефисом {sum("-" in w for w in cand)}')


def evaluate(a):
    """Как приложение: слово из словаря, иначе первый максимум акцентора среди гласных (как lang_stress)."""
    import torch
    from export_pack import HERE
    m = torch.package.PackageImporter(os.path.join(HERE, 'accentor-ukr.pt')).load_pickle('accentor_models', 'accentor').accentor.model
    m.embedding.weight.data = m.scale * (m.embedding.weight.data.clone() - m.zero_point)
    d = read_cand(a.eval)
    het = heteronyms(a.heteronyms)
    word = re.compile(r"[а-щьюяґєії'’ʼ+]+")
    items = []   # (слово, множество позиций ударной в золоте)
    with open(a.bench, encoding='utf-8') as f:
        for row in csv.DictReader(f):
            for g in word.findall(row['StressedSentence'].lower()):
                g = bare(g); w = g.replace('+', '')
                if sum(c in V for c in w) < 2 or '+' not in g: continue
                items.append((w, {i - 1 - g[:i].count('+') for i, c in enumerate(g) if c == '+'}))
    words = sorted({w for w, _ in items})
    with torch.no_grad():
        logits = dict(zip(words, m(words)[0].tolist()))
    stat = {}
    for w, gold in items:
        at = [j for j, c in enumerate(w) if c in V]; row = logits[w]
        acc = at[max(range(min(len(at), len(row))), key=row.__getitem__)]
        dic = d[w].index('+') if w in d else acc
        # служебные («коли»: коли́ / ко́ли) слой не размечает — что услышит модель, оценка не говорит, только акцентор
        for k in ('все', 'омографы' if w in het else '', 'вне словаря' if w not in d else '',
                  'служебные без «+»' if w in NO_MARK['uk'] else ''):
            if not k: continue
            s = stat.setdefault(k, [0, 0, 0, 0]); s[0] += 1; s[1] += acc in gold; s[2] += dic in gold; s[3] += w in d
    lines = [f'{os.path.basename(a.eval)}: {len(d)} слов; бенчмарк {os.path.basename(a.bench)}, слова с 2+ гласными']
    for k, (n, acc, dic, hit) in stat.items():
        lines.append(f'{k}: {n} слов, акцентор {acc / n:.1%}, акцентор + словарь {dic / n:.1%} (словарь знает {hit})')
    print('\n'.join(lines))


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--kaikki'); ap.add_argument('--eugenysilin'); ap.add_argument('--heteronyms', required=True)
    ap.add_argument('-o'); ap.add_argument('--eval'); ap.add_argument('--bench')
    a = ap.parse_args()
    evaluate(a) if a.eval else build(a)
