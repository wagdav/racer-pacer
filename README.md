# Racer Pacer

Race pace calculator in ClojureScript.

## Development

Interactive development
```
nix develop --command yarn install
nix develop --command clj -M:shadow-cljs watch app
```

Update dependencies

```
nix develop --command clj2nix deps.edn deps.nix
```

## Acknowledgements

I modeled the interactive behavior after [Bret Victor's reactive
documents](http://worrydream.com/Tangle/).

I learned from [David Nolen's
articles](https://swannodette.github.io/2013/08/17/comparative/) how to
decouple the concrete implementation concerns from the UI-logic and browser
callbacks.
