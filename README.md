# lambda-calculus-to-c

Implemented:

* untyped lambda calculus
* call by value evaluation order
* de Bruijn indices for nameless representation of terms
* Reference counting for freeing memory

Expressions in the grammar: 

```
expr ::= (fn name expr) | (expr expr) | name
name ::= [a-z]+
```
The follow is an example of code and the compiled C output

Lambda expression:

```
((fn x ((fn y x) (fn z z))) (fn w w))
```

Compiled output:

```
#include <stdio.h>
#include <stdlib.h>
typedef struct function {
    struct function* (*ptr)(struct function*, struct function**);
    struct function** closedValues;
    int references;
    int numClosedValues;
} function;

void del(function* f) {
    if(f->references > 1) f->references--;
    else {
        printf("freeing\n");
        fflush(stdout);

        for(int i = 0; i < f->numClosedValues; i++) del(f->closedValues[i]);
        free(f->closedValues);
        free(f);
    }
}

void dup(function* f) {
    f->references++;
}

function* f_r_f(function*, function**);
function* f_l_f_ret_r_f(function*, function**);
function* f_l_f_ret_l_f(function*, function**);
function* f_l_f(function*, function**);

function* f_r_f(function* f, function** c) {
    printf("f_r_f called\n");
    fflush(stdout);

    function* f_r_f_ret = f;
    dup(f);

    return f_r_f_ret;
}

function* f_l_f_ret_r_f(function* f, function** c) {
    printf("f_l_f_ret_r_f called\n");
    fflush(stdout);

    function* f_l_f_ret_r_f_ret = f;
    dup(f);

    return f_l_f_ret_r_f_ret;
}

function* f_l_f_ret_l_f(function* f, function** c) {
    printf("f_l_f_ret_l_f called\n");
    fflush(stdout);

    function* f_l_f_ret_l_f_ret = c[0];
    dup(c[0]);

    return f_l_f_ret_l_f_ret;
}

function* f_l_f(function* f, function** c) {
    printf("f_l_f called\n");
    fflush(stdout);

    function* f_l_f_ret_r = (function*)malloc(sizeof(function));
    f_l_f_ret_r->references = 1;
    f_l_f_ret_r->numClosedValues = 0;
    f_l_f_ret_r->ptr = &f_l_f_ret_r_f;
    f_l_f_ret_r->closedValues = (function**)malloc(sizeof(function*) * 0);
    function* f_l_f_ret_l = (function*)malloc(sizeof(function));
    f_l_f_ret_l->references = 1;
    f_l_f_ret_l->numClosedValues = 1;
    f_l_f_ret_l->ptr = &f_l_f_ret_l_f;
    f_l_f_ret_l->closedValues = (function**)malloc(sizeof(function*) * 1);
    f_l_f_ret_l->closedValues[0] = f;
    dup(f);
    function* f_l_f_ret = f_l_f_ret_l->ptr(f_l_f_ret_r, f_l_f_ret_l->closedValues);

    del(f_l_f_ret_r);
    del(f_l_f_ret_l);

    return f_l_f_ret;
}

int main() {
    function* f_r = (function*)malloc(sizeof(function));
    f_r->references = 1;
    f_r->numClosedValues = 0;
    f_r->ptr = &f_r_f;
    f_r->closedValues = (function**)malloc(sizeof(function*) * 0);
    function* f_l = (function*)malloc(sizeof(function));
    f_l->references = 1;
    f_l->numClosedValues = 0;
    f_l->ptr = &f_l_f;
    f_l->closedValues = (function**)malloc(sizeof(function*) * 0);
    function* f = f_l->ptr(f_r, f_l->closedValues);

    del(f_r);
    del(f_l);

    del(f);
    return 0;
}
```
