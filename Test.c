#include <stdio.h>
#include <stdlib.h>

typedef struct function {
    struct function* (*ptr)(struct function*, struct function**);
    struct function** closedValues;
} function;

function* ident(function* f, function** closedValues) {
    return f;
}

function* fun(function* f, function** closedValues) {
    return closedValues[0]->ptr(f, f->closedValues);
}

function* bla(function* f, function** closedValues) {
    function* g = (function*)malloc(sizeof(function));
    function** cv = (function**)malloc(sizeof(function*));
    cv[0] = f;
    g->ptr = &fun;
    g->closedValues = cv;
    return g;
}

int main() {
    function* f = (function*)malloc(sizeof(function));
    f->ptr = &ident;
    function* i = bla(f, 0);
    i->ptr(f, i->closedValues);
    return 0;
}
