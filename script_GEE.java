// Codi creat amb recomanador de Gemini.

var punts = ee.FeatureCollection('projects/tfmxcp/assets/gee_points');

var puntsAmbDades = punts.map(function(feature) {
  var data = ee.Date.parse('yyyy-MM-dd', feature.get('Data'));
  var dataStr = data.format('yyyy-MM-dd');
  var punt = feature.geometry();
  var sat = ee.String(feature.get('Satellit'));

  // Selecció de col·lecció MODIS segons satèl·lit
  var imatge = ee.Algorithms.If(
    sat.compareTo('T').eq(0),
    ee.ImageCollection('MODIS/006/MOD11A1'),
    ee.ImageCollection('MODIS/006/MYD11A1')
  );

  var lstImage = ee.ImageCollection(imatge)
    .filterDate(data, data.advance(1, 'day'))
    .filterBounds(punt)
    .first();

  var ndviImage = ee.ImageCollection('MODIS/006/MOD13A1')
    .filterDate(data.advance(-8, 'day'), data.advance(8, 'day'))
    .filterBounds(punt)
    .sort('system:time_start', false)
    .first();

  return ee.Algorithms.If(lstImage,
    ee.Algorithms.If(
      lstImage.select('LST_Day_1km')
        .sample({
          region: punt,
          scale: 1000,
          numPixels: 1,
          geometries: false
        })
        .first(),
      function() {
        var valor = lstImage.select('LST_Day_1km')
          .sample({
            region: punt,
            scale: 1000,
            numPixels: 1,
            geometries: false
          })
          .first();

        var ndvi = ee.Algorithms.If(ndviImage,
          ndviImage.select('NDVI')
            .multiply(0.0001)
            .reduceRegion({
              reducer: ee.Reducer.first(),
              geometry: punt,
              scale: 250,
              maxPixels: 1e8
            }).get('NDVI'),
          null
        );

        // btenim el valor decimal de la fracció del dia
        var viewTime = lstImage.select('Day_view_time')
          .reduceRegion({
            reducer: ee.Reducer.first(),
            geometry: punt,
            scale: 1000,
            maxPixels: 1e8
          }).get('Day_view_time');

        return feature.set({
          'LST_Day_1km': valor.get('LST_Day_1km'),
          'NDVI': ndvi,
          'Day_view_time': viewTime,
          'MODIS_date': dataStr,
          'lon': punt.coordinates().get(0),
          'lat': punt.coordinates().get(1)
        });
      }(),
      feature.set({
        'LST_Day_1km': null,
        'NDVI': null,
        'Day_view_time': null,
        'MODIS_date': dataStr,
        'lon': punt.coordinates().get(0),
        'lat': punt.coordinates().get(1)
      })
    ),
    feature.set({
      'LST_Day_1km': null,
      'NDVI': null,
      'Day_view_time': null,
      'MODIS_date': dataStr,
      'lon': punt.coordinates().get(0),
      'lat': punt.coordinates().get(1)
    })
  );
});

var resultats = ee.FeatureCollection(puntsAmbDades);
var valids = resultats.filter(ee.Filter.notNull(['LST_Day_1km']));

// Exportació final
Export.table.toDrive({
  collection: valids.select([
    'id', 'Data', 'Satellit',
    'MODIS_date', 'Day_view_time',
    'LST_Day_1km', 'NDVI', 'lat', 'lon'
  ]),
  description: 'LST_NDVI_GEE',
  fileFormat: 'CSV'
});
