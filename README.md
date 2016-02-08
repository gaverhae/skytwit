# skytwit

This is a learning project (like
[wikidump](https://github.com/gaverhae/wikidump)); its goal is for me to
explore a few technologies, including ClojureScript, Om, and the Twitter API.
Like wikidump, I urge you to not try to use this code for anything serious.

As it is now, it is a simple web page where you can enter a Twitter handle and
get a nice graph of that user's hashtag frequencies. At the time of working,
the very basic version of that works, though there's still room for a ton of
improvement. I probably won't touch this project for a week or so, so here are
some notes to my future self:

* It's still not entirely clear for me how
  [twitter-api](https://github.com/adamwynne/twitter-api) expects the user to
  actually handle errors. While it's clear that it has to do with the
  `:callbacks` option, it's really not clear how they work. Once I figure that
  out, maybe I should try to submit pull requests on the twitter-api project
  with additional documentation.
* Once errors are handled, it would be nice if they could be somehow propagated
  to the end user where it makes sense.
* An easier improvement would be to put some indication of the total progress
  on the web page: the Twitter API can tell me how many tweets a user has, and
  I can easily count the ones I have already fetched, so I should be able to
  display some kind of progress bar on that front.
* One of the main source of errors is "Rate limit exceeded"; while it is not so
  hard to understand and circumvent for the REST endpoint, I have no clear idea
  of chat it means or how to work around it for the streaming endpoint. I
  imagine more research would be needed there.
* Speaking of which, a nice improvement to the REST API stuff would be to use
  core.async to throttle the request rate, which could ensure that the rate
  limit is never reached (is it better than bursting through it and then
  handling the errors?).
* Adding tests. This may be linked to the rate throttling idea, but at this
  point there are no tests and I have no idea how to add any valuable one.
  Maybe if I hide the Twitter API behind core.async channels I will be able to
  both test the rest of the application (mocking the other side of a channel
  should be easy) and throttle the connection? That may also work for the
  communication between the Clojure and ClojureScript apps.
* Find out how to connect Fireplace to the browser REPL. I really miss the
  interactivity.
* Maybe explore Sente a bit more and think about allowing different users to
  look at different data? At the moment, everyone connected to the same server
  will see the same data and change it for everyone else. I am not sure how that
  would work with the (very strict) API rate limits, though. Maybe once I have
  finished throttling. Or maybe allow the users to authenticate, so they use
  their own limits rather than mine? Not even sure that can work, but it would
  be worth investigating.
* The design of the web page could obviously be much better.

## Development

The app needs a few environment variables to run (even for development), so it
should be started with:


```bash
TWITTER_HANDLE=your-handle \
OAUTH_APP_KEY="an-app-token" \
OAUTH_APP_SECRET="an-app-secret" \
OAUTH_CONSUMER_KEY="a-consumer-key" \
OAUTH_CONSUMER_SECRET="a-consumer-secret" \
lein repl
```

>
> The rest of this README is still the stock Chestnut README.
>

Open a terminal and type `lein repl` to start a Clojure REPL
(interactive prompt).

In the REPL, type

```clojure
(run)
(browser-repl)
```

The call to `(run)` starts the Figwheel server at port 3449, which takes care of
live reloading ClojureScript code and CSS. Figwheel's server will also act as
your app server, so requests are correctly forwarded to the http-handler you
define.

Running `(browser-repl)` starts the Weasel REPL server, and drops you into a
ClojureScript REPL. Evaluating expressions here will only work once you've
loaded the page, so the browser can connect to Weasel.

When you see the line `Successfully compiled "resources/public/app.js" in 21.36
seconds.`, you're ready to go. Browse to `http://localhost:3449` and enjoy.

**Attention: It is not needed to run `lein figwheel` separately. Instead we
launch Figwheel directly from the REPL**

## Trying it out

If all is well you now have a browser window saying 'Hello Chestnut',
and a REPL prompt that looks like `cljs.user=>`.

Open `resources/public/css/style.css` and change some styling of the
H1 element. Notice how it's updated instantly in the browser.

Open `src/cljs/skytwit/core.cljs`, and change `dom/h1` to
`dom/h2`. As soon as you save the file, your browser is updated.

In the REPL, type

```
(ns skytwit.core)
(swap! app-state assoc :text "Interactivity FTW")
```

Notice again how the browser updates.

### Lighttable

Lighttable provides a tighter integration for live coding with an inline
browser-tab. Rather than evaluating cljs on the command line with weasel repl,
evaluate code and preview pages inside Lighttable.

Steps: After running `(run)`, open a browser tab in Lighttable. Open a cljs file
from within a project, go to the end of an s-expression and hit Cmd-ENT.
Lighttable will ask you which client to connect. Click 'Connect a client' and
select 'Browser'. Browse to [http://localhost:3449](http://localhost:3449)

View LT's console to see a Chrome js console.

Hereafter, you can save a file and see changes or evaluate cljs code (without saving a file). Note that running a weasel server is not required to evaluate code in Lighttable.

### Emacs/Cider

Start a repl in the context of your project with `M-x cider-jack-in`.

Switch to repl-buffer with `C-c C-z` and start web and figwheel servers with
`(run)`, and weasel server with `(browser-repl`). Load
[http://localhost:3449](http://localhost:3449) on an external browser, which
connects to weasel, and start evaluating cljs inside Cider.

To run the Clojurescript tests, do

```
lein doo phantom
```

## Deploying to Heroku

This assumes you have a
[Heroku account](https://signup.heroku.com/dc), have installed the
[Heroku toolbelt](https://toolbelt.heroku.com/), and have done a
`heroku login` before.

``` sh
git init
git add -A
git commit
heroku create
git push heroku master:master
heroku open
```

## Running with Foreman

Heroku uses [Foreman](http://ddollar.github.io/foreman/) to run your
app, which uses the `Procfile` in your repository to figure out which
server command to run. Heroku also compiles and runs your code with a
Leiningen "production" profile, instead of "dev". To locally simulate
what Heroku does you can do:

``` sh
lein with-profile -dev,+production uberjar && foreman start
```

Now your app is running at
[http://localhost:5000](http://localhost:5000) in production mode.
## License

Copyright © 2016 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

## Chestnut

Created with [Chestnut](http://plexus.github.io/chestnut/) 0.9.1 (3a675806).
