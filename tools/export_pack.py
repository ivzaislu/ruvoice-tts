"""Пак голосов Silero v5 для RuVoice (спека 2026-09-18):
dist/ruvoice-pack-<id>.zip (pack.json + tts_mel.ptl + backbone.pte + head.ptl); для cis_ru ещё
app/src/test/resources/golden_pack.json (pack.json и ids golden-фразы для JVM-теста).
Пак не на русском (LANGS) несёт ещё accentor.ptl — акцентор своего языка — и stress.txt, поправки ударений
«слово сл+ово»: слова, где lite-акцентор ошибается, из exceptions того же акцентора (MIT) и, с --grammardb, из
Беларускай граматычнай базы (https://github.com/Belarus/GrammarDB, CC BY-SA 4.0, файл релиза
spellchecker/slovy-2008-stress.txt); --stress подменяет stress.txt своим файлом (и у русских паков); --morph кладёт
morph.bin — морфологию языка (tools/gdb_morph.py, та же GrammarDB).
Украинские cis_uk (v5_cis_base_nostress, MIT) и cis_uk_ext (v5_cis_ext, CC BY-NC 4.0): акцентор accentor-ukr без
exceptions, поправки — --cand, кандидаты «слово сл+ово» из tools/uk_stress.py (Викисловарь через kaikki.org и
eugenysilin/russian-ukrainian-stress-data, CC BY-SA 4.0); --morph — tools/vesum_morph.py (VESUM, CC BY-NC-SA 4.0).
Запуск: ../venv-et/bin/python tools/export_pack.py cis_ru | ru | cis_be | cis_uk | cis_uk_ext [--grammardb путь]
[--cand путь] [--stress путь] [--morph путь]
(модель и акцентор для id скачать заранее в tools/, см. source в PACKS и LANGS)"""
import argparse, json, os, tempfile, zipfile
from typing import List, Tuple
import torch
from torch.jit.mobile import _load_for_lite_interpreter
from silero_export import split_tts, export_backbone, verify_backbone

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
DIST = os.path.join(ROOT, 'dist')
GOLDEN = os.path.join(ROOT, 'app/src/test/resources/golden_pack.json')
FORMAT = 2   # Packs.FORMAT в приложении: три файла моделей; число аргументов forward задаёт types в pack.json
# пак не на русском: 1.0.1 поля lang не знает и читал бы его русским слоем, поэтому формат другой — старое
# приложение такой пак отвергает. В pack.json ещё lang и default (голос языка по умолчанию = golden-голос);
# у русских паков этих полей нет и формат прежний, чтобы их принимали и старые приложения
FORMAT_LANG = 3
# id: (lang, акцентор языка) — accentor-<lang>.pt из pip-пакета silero-stress (MIT, silero_stress/data/) скопировать в tools/
LANGS = {'cis_be': ('be', 'accentor-bel.pt'), 'cis_uk': ('uk', 'accentor-ukr.pt'), 'cis_uk_ext': ('uk', 'accentor-ukr.pt')}
PACKS = {
    # id: (файл модели, фильтр голосов, types, title, license, source, golden-голос)
    'cis_ru': ('v5_cis_base_nostress.pt', lambda s: s.startswith('ru_'), False, 'Русские голоса Silero v5 CIS — экспериментально', 'MIT',
               'https://models.silero.ai/models/tts/ru/v5_cis_base_nostress.pt', 'ru_alexandr'),
    'ru': ('v5_5_ru.pt', lambda s: True, True, 'Штатные голоса Silero v5.5', 'CC BY-NC-SA 4.0',
           'https://models.silero.ai/models/tts/ru/v5_5_ru.pt', 'xenia'),
    'cis_be': ('v5_cis_base_nostress.pt', lambda s: s.startswith('bel_'), False, 'Белорусские голоса Silero v5 CIS', 'MIT',
               'https://models.silero.ai/models/tts/ru/v5_cis_base_nostress.pt', 'bel_larisa'),
    'cis_uk': ('v5_cis_base_nostress.pt', lambda s: s.startswith('ukr_'), False, 'Украинские голоса Silero v5 CIS', 'MIT',
               'https://models.silero.ai/models/tts/ru/v5_cis_base_nostress.pt', 'ukr_igor'),
    'cis_uk_ext': ('v5_cis_ext.pt', lambda s: s.startswith('ukr_'), False, 'Украинские голоса Silero v5 CIS ext', 'CC BY-NC 4.0',
                   'https://models.silero.ai/models/tts/ru/v5_cis_ext.pt', 'ukr_mykyta'),
}
TEXT = 'прив+ет, м+ир.'
# язык: (источники --cand для строки лицензии пака, они же в шапке stress.txt)
CAND = {'uk': ('Wiktionary via kaikki.org, eugenysilin/russian-ukrainian-stress-data',
               'Викисловарь\n# (английский, через kaikki.org, https://kaikki.org/dictionary/Ukrainian/) и eugenysilin/russian-ukrainian-stress-data\n'
               '# (https://github.com/eugenysilin/russian-ukrainian-stress-data), CC BY-SA 4.0; tools/uk_stress.py;\n'
               '# файл распространяется на условиях CC BY-SA 4.0\n')}


def tts_args(pk, text, sr=48000):
    """Тензоры для forward: seq, sr, rates, pitches; speaker, type_ids и focus_mask подставляет вызывающий."""
    seq, _ = pk.preprocess_tacotron(text); seq = seq.unsqueeze(0); n = seq.shape[1]
    return seq, sr, torch.ones(1, n), torch.ones(1, n)


def forward(model, pk, text, speaker_id, types, sr=48000):
    """11 аргументов forward (как у v5_ru) или 13 (v5_5_ru: type_ids нули, focus_mask None)."""
    seq, sr, rates, pitches = tts_args(pk, text, sr)
    args = [seq, torch.LongTensor([speaker_id]), sr, None, rates, pitches, None, None, 'cpu', -1, False]
    if types: args += [torch.zeros(1, seq.shape[1], dtype=torch.long), None]
    return model(*args)


class Accentor2(torch.nn.Module):
    """Акцентор silero-stress для bel/ukr отдаёт (logits, None), а приложение берёт out[1] тензором
    (у русского там логиты «ё»): вторым выходом нули [n, 1] — «ё» эти акценторы не ставят."""
    def __init__(self, m):
        super().__init__()
        self.m = m

    def forward(self, words: List[str]) -> Tuple[torch.Tensor, torch.Tensor]:
        logits = self.m(words)[0]
        return logits, torch.zeros(logits.size(0), 1)


def load_accentor(pt):
    """accentor-<lang>.pt из silero-stress: объект с model и exceptions. Эмбеддинги n-грамм в пакете int8 —
    деквантуем, как load_accentor в silero-stress."""
    a = torch.package.PackageImporter(pt).load_pickle('accentor_models', 'accentor').accentor
    m = a.model
    m.embedding.weight.data = m.scale * (m.embedding.weight.data.clone() - m.zero_point)
    return a


def export_accentor(pt, out_path, cand=()):
    """accentor-<lang>.pt → accentor.ptl. Проверка: lite против исходной модели на 50 словах из её exceptions
    (у ukr их нет — из cand), логиты один в один."""
    a = load_accentor(pt); m = a.model
    torch.jit.script(Accentor2(m))._save_for_lite_interpreter(out_path)
    src = sorted(a.exceptions or cand)
    words = src[::max(1, len(src) // 50)][:50]
    assert words, 'нет слов для проверки акцентора: exceptions пусты, нужен --cand'
    lite = _load_for_lite_interpreter(out_path)
    with torch.no_grad():
        ls, ly = lite(words); out = m(words)
    # bel и ukr отдают (logits, None) — обёртка Accentor2 рассчитана ровно на это (у русского второй выход — «ё»)
    assert isinstance(out, tuple) and len(out) == 2 and out[1] is None, f'выходы акцентора: {type(out)}'
    assert (ls - out[0]).abs().max().item() == 0 and ly.shape == (len(words), 1), 'accentor mismatch'
    return a.exceptions, lite


# как в LangStress.kt (и в украинском слое): гласные, служебные слова без «+» по языку пака; апостроф слой убирает
# до ударений
VOWELS = {'be': 'аеёіоуыэюя', 'uk': 'аеєиіїоуюя'}
NO_MARK = {
    'be': {'ў', 'у', 'з', 'с', 'на', 'да', 'па', 'за', 'ад', 'аб', 'для', 'пры', 'між', 'пад', 'над', 'без', 'праз',
           'пра', 'а', 'і', 'не', 'ні', 'бы', 'жа', 'ці', 'то', 'як', 'што', 'хто', 'дзе', 'калі', 'так', 'ну', 'вось',
           'чым', 'каб', 'але', 'або', 'усе', 'усё'},
    'uk': set('у в з із зі зо о об на до по за від од для при між під над без про а і й та не ні б би ж же чи то як що '
              'хто де коли так ну ось щоб але або аби це'.split()),
}
APOS = "’'`´ʼ‘"


def markable(part, lang):
    """Часть слова, которой LangStress ставит «+»: не служебное слово и не меньше двух гласных."""
    return part not in NO_MARK[lang] and sum(c in VOWELS[lang] for c in part) >= 2


def normalize(v, lang):
    """Ударения так, как их поставит LangStress: «+» только в размечаемых частях, в каждой ровно один; иначе None."""
    parts = []
    for p in v.split('-'):
        bare = p.replace('+', '')
        if not markable(bare, lang): parts.append(bare); continue
        if p.count('+') != 1 or p.endswith('+') or p[p.index('+') + 1] not in VOWELS[lang]: return None
        parts.append(p)
    return '-'.join(parts) if '+' in ''.join(parts) else None


def acute_to_plus(s, letters):
    """Форма с акутом (и грависом) после ударной гласной → «+» перед ней; нижний регистр, апостроф снят. None — пусто,
    буквы не из letters или дефис с края."""
    s = s.strip().lower()
    for a in APOS: s = s.replace(a, '')
    out = []
    for ch in s:
        if ch in '́̀':
            if out: out.insert(len(out) - 1, '+')
        else:
            out.append(ch)
    v = ''.join(out); w = v.replace('+', '')
    return v if w and set(w) <= letters and w[0] != '-' and w[-1] != '-' else None


def first_max(p, row, lang):
    """Разметка части слова акцентором, как LangStress.kt: первый максимум логитов среди гласных, но не дальше выходов
    модели; p с «+» перед ударной."""
    at = [j for j, c in enumerate(p) if c in VOWELS[lang]]
    k = max(range(min(len(at), len(row))), key=row.__getitem__)
    return p[:at[k]] + '+' + p[at[k]:]


def grammardb_stress(path):
    """GrammarDB slovy-2008-stress.txt: форма с акутом после ударной гласной → «+» перед ней. Только слова с одним
    вариантом ударения: омографы («за+мак»/«зам+ок») одним ударением не фиксируем."""
    variants = {}
    letters = set('абвгдеёжзійклмнопрстуўфхцчшыьэюя-')
    with open(path, encoding='utf-8') as f:
        for line in f:
            v = acute_to_plus(line, letters)
            if v: variants.setdefault(v.replace('+', ''), set()).add(normalize(v, 'be'))
    return {w: next(iter(vs)) for w, vs in variants.items() if len(vs) == 1 and None not in vs}


def read_cand(path):
    """Кандидаты «слово сл+ово» (tools/uk_stress.py), «#» — комментарий."""
    with open(path, encoding='utf-8') as f:
        return dict(line.split() for line in f if line.strip() and not line.startswith('#'))


def lang_stress(exceptions, lite, path, lang, cand, head):
    """stress.txt: словарные ударения, которые lite-акцентор в приложении ставит иначе. Источники: exceptions
    акцентора silero-stress (слово → (позиция «+», позиция «ё»; у bel «ё» всегда -1); python-акцентор ставит их
    сам, приложению достаётся только модель) и cand — словарные кандидаты (GrammarDB у be, --cand у uk); при
    расхождении побеждают exceptions. head — шапка файла: источники и лицензия.
    Разметку акцентором повторяем как LangStress.kt: слово режется по дефису, в каждой размечаемой части —
    первый максимум среди гласных, но не дальше выходов модели."""
    cand = dict(cand)
    n_cand = len(cand)
    exc = {}
    for w, (s, _) in exceptions.items():
        bare = w
        for a in APOS: bare = bare.replace(a, '')
        k = s - sum(c in APOS for c in w[:s])
        v = normalize(bare[:k] + '+' + bare[k:], lang)
        if v: exc[bare] = v
    conflicts = sum(1 for w, v in exc.items() if w in cand and cand[w] != v)
    cand.update(exc)
    parts = sorted({p for w in cand for p in w.split('-') if markable(p, lang)})
    pred = {}
    with torch.no_grad():
        for i in range(0, len(parts), 4096):
            chunk = parts[i:i + 4096]
            for p, row in zip(chunk, lite(chunk)[0].tolist()):
                pred[p] = first_max(p, row, lang)
    out = [f'{w} {v}\n' for w, v in sorted(cand.items()) if '-'.join(pred.get(p, p) for p in w.split('-')) != v]
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(head)
        f.writelines(out)
    print(f'stress.txt: {len(out)} поправок из {len(cand)} слов (exceptions {len(exc)}, словарь {n_cand}, '
          f'расхождений exceptions и словаря {conflicts})')


def check_stress(path):
    """stress.txt: UTF-8, строки «слово сл+ово» (DictLines.parseStress), «#» — комментарий."""
    with open(path, encoding='utf-8') as f:
        for i, line in enumerate(f, 1):
            p = line.split()
            assert not p or p[0].startswith('#') or (len(p) == 2 and '+' in p[1] and p[1].replace('+', '') == p[0]), f'{path}:{i}: {line!r}'


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('pack_id', nargs='?', default='cis_ru', choices=PACKS)
    ap.add_argument('--stress', help='stress.txt в пак (поправки ударений) вместо собранного из exceptions')
    ap.add_argument('--grammardb', help='slovy-2008-stress.txt из релиза GrammarDB: поправки и из неё (CC BY-SA 4.0)')
    ap.add_argument('--cand', help='кандидаты «слово сл+ово» из tools/uk_stress.py: поправки и из них (CC BY-SA 4.0)')
    ap.add_argument('--morph', help='morph.bin в пак: морфология языка (tools/gdb_morph.py из GrammarDB, CC BY-SA 4.0; '
                                    'tools/vesum_morph.py из VESUM, CC BY-NC-SA 4.0)')
    args = ap.parse_args()
    pack_id = args.pack_id
    pt, keep, types, title, lic, source, golden_speaker = PACKS[pack_id]
    lang = LANGS.get(pack_id, ('ru',))[0]
    if args.grammardb and not args.stress: lic += '; stress.txt — CC BY-SA 4.0 (GrammarDB)'
    if args.cand and lang not in CAND: ap.error(f'--cand: источник кандидатов для языка «{lang}» не описан в CAND')
    if args.cand and not args.stress: lic += f'; stress.txt — CC BY-SA 4.0 ({CAND[lang][0]})'
    if args.morph: lic += '; morph.bin — ' + ('CC BY-NC-SA 4.0 (VESUM, brown-uk/dict_uk)' if lang == 'uk' else 'CC BY-SA 4.0 (GrammarDB)')
    cand, head = {}, ''
    if pack_id in LANGS:
        acc = LANGS[pack_id][1][9:-3]
        head = f'# слова, где акцентор ставит ударение иначе: исключения акцентора silero-stress ({acc}, MIT)\n'
        if args.grammardb:
            cand = grammardb_stress(args.grammardb)
            head += ('# и Беларуская граматычная база (GrammarDB, https://github.com/Belarus/GrammarDB, CC BY-SA 4.0);\n'
                     '# файл распространяется на условиях CC BY-SA 4.0\n')
        if args.cand:
            got = read_cand(args.cand)
            ok = {w: v for w, v in got.items() if normalize(v, lang) == v}
            print(f'--cand: {len(got)} слов, отброшено normalize {len(got) - len(ok)}')
            cand.update(ok)
            head = f'# слова, где акцентор silero-stress ({acc}, MIT) ставит ударение иначе, чем словарь: {CAND[lang][1]}'
    PT = os.path.join(HERE, pt)
    big = torch.package.PackageImporter(PT).load_pickle('tts_models', 'model')
    pk = big.packages[0]
    assert len(big.packages) == 1 and len(pk.models) == 1, 'ожидалась одна модель'
    assert (pk.accentor is None) == (not types), 'акцентор не соответствует types'
    tts = pk.models[0]
    speakers = {s: i for s, i in pk.speaker_to_ids[0].items() if keep(s)}
    assert len(speakers) == {'cis_ru': 29, 'ru': 5, 'cis_be': 3, 'cis_uk': 2, 'cis_uk_ext': 5}[pack_id], len(speakers)
    if args.stress: check_stress(args.stress)
    pack = {
        'format': FORMAT, 'id': pack_id, 'title': title, 'license': lic,
        'source': source,
        'symbols': pk.symbols, 'symbol_to_id': pk.symbol_to_id, 'sos': pk.sos_token, 'eos': pk.eos_token,
        'alphabet': ''.join(pk.alphabet), 'types': types, 'speakers': speakers,
    }
    if pack_id in LANGS: pack.update(format=FORMAT_LANG, lang=LANGS[pack_id][0], default=golden_speaker)
    sid = speakers[golden_speaker]
    with torch.no_grad():  # эталоны до хирургии графа
        ref, ref_durs = forward(tts, pk, TEXT, sid, types)
        ref24, _ = forward(tts, pk, TEXT, sid, types, 24000)
    os.makedirs(DIST, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        mel_p, head_p, bb_p = (os.path.join(tmp, n) for n in ('tts_mel.ptl', 'head.ptl', 'backbone.pte'))
        split_tts(tts, mel_p, head_p)
        lite_m = _load_for_lite_interpreter(mel_p); lite_head = _load_for_lite_interpreter(head_p)
        with torch.no_grad():
            mel, durs = forward(lite_m, pk, TEXT, sid, types)
            ref_vocoder = tts.vocoder.backbone(mel)
            a = lite_head(ref_vocoder, 48000, 0., True); a24 = lite_head(ref_vocoder, 24000, 0., True)
        assert (a - ref).abs().max().item() == 0 and (a24 - ref24).abs().max().item() == 0, 'tts_mel+head mismatch'
        assert torch.equal(durs, ref_durs), 'durs mismatch'
        export_backbone(tts.vocoder.backbone.state_dict(), mel.contiguous(), bb_p)
        d = verify_backbone(bb_p, head_p, mel.contiguous(), ref)
        assert d < 1e-3, f'pte mismatch {d}'
        files = [('tts_mel.ptl', mel_p), ('backbone.pte', bb_p), ('head.ptl', head_p)]
        if pack_id in LANGS:
            acc_p = os.path.join(tmp, 'accentor.ptl'); exc, lite = export_accentor(os.path.join(HERE, LANGS[pack_id][1]), acc_p, cand)
            files.append(('accentor.ptl', acc_p))
            if not args.stress: args.stress = os.path.join(tmp, 'stress.txt'); lang_stress(exc, lite, args.stress, lang, cand, head)
            check_stress(args.stress)
        out = os.path.join(DIST, f'ruvoice-pack-{pack_id}.zip')
        with zipfile.ZipFile(out, 'w') as zf:
            zf.writestr('pack.json', json.dumps(pack, ensure_ascii=False), compress_type=zipfile.ZIP_DEFLATED)
            for n, p in files:
                zf.write(p, n, compress_type=zipfile.ZIP_STORED)
            if args.stress: zf.write(args.stress, 'stress.txt', compress_type=zipfile.ZIP_DEFLATED)
            if args.morph: zf.write(args.morph, 'morph.bin', compress_type=zipfile.ZIP_STORED)
    if pack_id == 'cis_ru':
        seq, _ = pk.preprocess_tacotron(TEXT)
        for p in (GOLDEN, os.path.join(ROOT, 'app/src/androidTest/assets/golden_pack.json')):
            with open(p, 'w', encoding='utf-8') as f:
                json.dump({'pack': pack, 'text': TEXT, 'speaker': golden_speaker, 'ids': seq.tolist()}, f, ensure_ascii=False, indent=1)
    print('verify: ok, pte maxdiff', d)
    print(out, round(os.path.getsize(out) / 1048576, 1), 'MB;', len(speakers), 'голосов')
