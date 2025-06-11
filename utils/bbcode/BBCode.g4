grammar BBCode;

//options { tokenVocab=BBCodeLexer; }

file: section EOF;

section : element+ ;

element: 
    b | i | u | s | code | 
    url | url_named | img | quote | size | color | mask | bgm_sticker | text_stiker |  
    plain;

plain: (TEXT | NUMBER | '(' | ')' | '[' | '/' | ']' | '=' | ',')+;

b: ('[b]' | '[B]') content=section? ('[/b]' | '[/B]');
i: ('[i]' | '[I]') content=section? ('[/i]' | '[/I]');
u: ('[u]' | '[U]') content=section? ('[/u]' | '[/U]');
s: ('[s]' | '[S]') content=section? ('[/s]' | '[/S]');
code: ('[code]' | '[CODE]') content=section? ('[/code]' | '[/CODE]');
mask: ('[mask]' | '[MASK]') content=section? ('[/mask]' | '[/MASK]');
quote: ('[quote]' | '[QUOTE]') content=section? ('[/quote]' | '[/QUOTE]');
size: ('[size=' | '[SIZE=') value=NUMBER (']' | ']') content=section? ('[/size]' | '[/SIZE]');
color: ('[color=' | '[COLOR=') value=TEXT (']' | ']') content=section? ('[/color]' | '[/COLOR]');

bgm_sticker: ('(bgm' | '(BGM') id=NUMBER ')';
text_stiker: 
      '(=A=)'
    | '(=w=)'
    | '(-w=)'
    | '(S_S)'
    | '(=v=)'
    | '(@_@)'
    | '(=W=)'
    | '(TAT)'
    | '(T_T)'
    | '(=\'=)'
    | '(=3=)'
    | '(= =\')'
    | '(=///=)'
    | '(=.,=)'
    | '(:P)'
    | '(LOL)';

url: ('[url]' | '[URL]') href=plain? ('[/url]' | '[/URL]');
url_named: ('[url=' | '[URL=') href=attribute_value ']' content=section? ('[/url]' | '[/URL]');
img: ('[img' | '[IMG') ']' content=plain+ ('[/img]' | '[/IMG]');

attribute_value: quoted=QUOTED | unquoted=TEXT;

// Lexer

NUMBER: [0-9]+ ;

QUOTED
   : '"' TEXT '"'
   ;

// Not quoted
TEXT: ~[[\]()]+ ;

//fragment ESC
//   : '\\' (["\\/bfnrt])
//   ;
//
//fragment SAFECODEPOINT
//   : ~ ["\\\u0000-\u001F]
//   ;